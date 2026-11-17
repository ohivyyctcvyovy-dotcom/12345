#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <string>
#include <vector>
#include <mutex>
#include <sys/stat.h>
#include <unistd.h>
#include "stable-diffusion.h"

namespace {
thread_local std::string g_last_error;

std::mutex g_context_mutex;
sd_ctx_t* g_cached_context = nullptr;
int g_cached_fd = -1;
dev_t g_cached_device = 0;
ino_t g_cached_inode = 0;

struct ProgressData {
    JNIEnv* env;
    jobject activity;
    jmethodID method;
};

void notify_java(ProgressData* data, const char* stage, jlong completed = 0, jlong total = 0) {
    if (!data || !data->env || !data->activity || !data->method) return;
    jstring text = data->env->NewStringUTF(stage ? stage : "Processando imagem...");
    if (!text) return;
    data->env->CallVoidMethod(data->activity, data->method, text, completed, total);
    data->env->DeleteLocalRef(text);
    if (data->env->ExceptionCheck()) data->env->ExceptionClear();
}

void generation_progress(const sd_generation_progress_t* progress, void* opaque) {
    auto* data = static_cast<ProgressData*>(opaque);
    if (!progress) {
        notify_java(data, "Gerando imagem...");
        return;
    }
    std::string label = "Processando imagem";
    if (progress->stage && progress->stage[0]) label = progress->stage;
    if (progress->detail && progress->detail[0]) label += std::string(": ") + progress->detail;
    notify_java(data, label.c_str(), (jlong)progress->completed, (jlong)progress->total);
}

void set_error(const std::string& value) {
    g_last_error = value.empty() ? "Falha desconhecida no motor de imagens." : value;
}

bool set_string(sd_opt_t* options, const char* key, const char* value) {
    const sd_status_t status = sd_opt_set_string(options, key, value);
    if (status != SD_STATUS_OK) {
        const char* detail = sd_get_last_error();
        set_error(detail ? detail : std::string("Não foi possível configurar ") + key);
        return false;
    }
    return true;
}

bool set_int(sd_opt_t* options, const char* key, int64_t value) {
    const sd_status_t status = sd_opt_set_int(options, key, value);
    if (status != SD_STATUS_OK) {
        const char* detail = sd_get_last_error();
        set_error(detail ? detail : std::string("Não foi possível configurar ") + key);
        return false;
    }
    return true;
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_imagelab_ai_MainActivity_nativeLastError(JNIEnv* env, jobject) {
    return env->NewStringUTF(g_last_error.c_str());
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_imagelab_ai_MainActivity_nativeGenerate(
        JNIEnv* env, jobject activity, jobject parcelFileDescriptor, jstring promptValue,
        jint width, jint height, jint threadCount) {
    g_last_error.clear();
    if (!parcelFileDescriptor || !promptValue) {
        set_error("Selecione um modelo e escreva uma descrição.");
        return nullptr;
    }

    jclass pfdClass = env->GetObjectClass(parcelFileDescriptor);
    jmethodID getFd = env->GetMethodID(pfdClass, "getFd", "()I");
    if (!getFd) {
        set_error("Não foi possível acessar o arquivo do modelo.");
        return nullptr;
    }
    const jint fd = env->CallIntMethod(parcelFileDescriptor, getFd);
    if (env->ExceptionCheck() || fd < 0) {
        env->ExceptionClear();
        set_error("O arquivo do modelo não está disponível.");
        return nullptr;
    }

    const char* promptChars = env->GetStringUTFChars(promptValue, nullptr);
    if (!promptChars) {
        set_error("Não foi possível ler a descrição da imagem.");
        return nullptr;
    }
    const std::string prompt(promptChars);
    env->ReleaseStringUTFChars(promptValue, promptChars);

    char modelPath[64];
    std::snprintf(modelPath, sizeof(modelPath), "/proc/self/fd/%d", fd);

    jclass activityClass = env->GetObjectClass(activity);
    jmethodID progressMethod = activityClass ? env->GetMethodID(
            activityClass, "onNativeProgress", "(Ljava/lang/String;JJ)V") : nullptr;
    ProgressData progressData{env, activity, progressMethod};

    // Keep one loaded context for the app process. Reloading a 683 MB model
    // on every tap caused unnecessary waits and repeated memory pressure.
    std::lock_guard<std::mutex> contextLock(g_context_mutex);
    struct stat modelStat {};
    if (fstat(fd, &modelStat) != 0) {
        set_error("Não foi possível identificar o arquivo do modelo.");
        return nullptr;
    }
    const bool sameModel = g_cached_context && g_cached_fd >= 0 &&
            g_cached_device == modelStat.st_dev && g_cached_inode == modelStat.st_ino;
    if (!sameModel && g_cached_context) {
        sd_ctx_free(g_cached_context);
        g_cached_context = nullptr;
        if (g_cached_fd >= 0) close(g_cached_fd);
        g_cached_fd = -1;
    }

    if (!sameModel) {
        g_cached_fd = dup(fd);
        if (g_cached_fd < 0) {
            set_error("Não foi possível manter o arquivo do modelo aberto.");
            return nullptr;
        }
        std::snprintf(modelPath, sizeof(modelPath), "/proc/self/fd/%d", g_cached_fd);
        g_cached_device = modelStat.st_dev;
        g_cached_inode = modelStat.st_ino;
        notify_java(&progressData, "Carregando o modelo de IA no motor...");
    } else {
        notify_java(&progressData, "Modelo já carregado. Preparando nova imagem...");
    }

    sd_ctx_t* context = g_cached_context;
    sd_ctx_opt_t* contextOptions = sd_ctx_opt_create();
    sd_img_gen_opt_t* imageOptions = nullptr;
    sd_result_t* result = nullptr;
    jintArray javaPixels = nullptr;

    if (!contextOptions) {
        set_error("Memória insuficiente para iniciar as opções do motor.");
        goto cleanup;
    }
    if (!set_string(contextOptions, "model_path", modelPath) ||
        !set_int(contextOptions, "n_threads", std::max(1, std::min(4, (int)threadCount)))) {
        goto cleanup;
    }
    // Memory mapping avoids eagerly copying the complete model into app memory.
    if (sd_opt_set_bool(contextOptions, "enable_mmap", true) != SD_STATUS_OK) {
        const char* detail = sd_get_last_error();
        set_error(detail ? detail : "O motor não aceitou o modo de memória econômica.");
        goto cleanup;
    }

    if (!context) {
        const sd_status_t status = sd_ctx_create(contextOptions, &context);
        if (status != SD_STATUS_OK || !context) {
            const char* detail = sd_get_last_error();
            set_error(detail ? detail : "Não foi possível carregar o modelo neste aparelho.");
            close(g_cached_fd);
            g_cached_fd = -1;
            g_cached_device = 0;
            g_cached_inode = 0;
            goto cleanup;
        }
        g_cached_context = context;
    }
    notify_java(&progressData, "Modelo carregado. Preparando a geração...");
    sd_set_generation_progress_callback(context, generation_progress, &progressData);

    imageOptions = sd_img_gen_opt_create();
    if (!imageOptions) {
        set_error("Memória insuficiente para preparar a imagem.");
        goto cleanup;
    }

    if (!set_string(imageOptions, "prompt", prompt.c_str()) ||
        !set_int(imageOptions, "width", width) ||
        !set_int(imageOptions, "height", height) ||
        !set_int(imageOptions, "seed",
                 (int64_t)std::chrono::system_clock::now().time_since_epoch().count()) ||
        !set_int(imageOptions, "sample.steps", 1)) {
        goto cleanup;
    }
    // SDXS distilled checkpoints require CFG 1 and a single sampling step.
    if (sd_opt_set_float(imageOptions, "sample.guidance.txt_cfg", 1.0) != SD_STATUS_OK) {
        const char* detail = sd_get_last_error();
        set_error(detail ? detail : "O modelo não aceitou a configuração de geração SDXS.");
        goto cleanup;
    }

    notify_java(&progressData, "Gerando a imagem. O tempo depende do processador do celular...");
    {
        const sd_status_t status = sd_generate_image(context, imageOptions, &result);
        if (status != SD_STATUS_OK || !result) {
            const char* detail = sd_get_last_error();
            set_error(detail ? detail : "A geração da imagem falhou.");
            goto cleanup;
        }
    }

    if (sd_result_get_image_count(result) < 1) {
        set_error("O motor terminou sem produzir uma imagem.");
        goto cleanup;
    }

    {
        const sd_tensor_t* image = sd_result_get_image(result, 0);
        if (!image || sd_tensor_type(image) != SD_TENSOR_U8 || sd_tensor_rank(image) != 3) {
            set_error("O motor retornou um formato de imagem não reconhecido.");
            goto cleanup;
        }
        const int64_t channels = sd_tensor_dim(image, 0);
        const int64_t imageWidth = sd_tensor_dim(image, 1);
        const int64_t imageHeight = sd_tensor_dim(image, 2);
        const auto* bytes = static_cast<const uint8_t*>(sd_tensor_data(image));
        if (!bytes || channels < 3 || imageWidth != width || imageHeight != height ||
            imageWidth * imageHeight > 16000000) {
            set_error("O tamanho da imagem retornada não é válido.");
            goto cleanup;
        }

        std::vector<jint> pixels((size_t)imageWidth * (size_t)imageHeight);
        for (int64_t y = 0; y < imageHeight; ++y) {
            for (int64_t x = 0; x < imageWidth; ++x) {
                const size_t offset = (size_t)((y * imageWidth + x) * channels);
                const uint8_t red = bytes[offset];
                const uint8_t green = bytes[offset + 1];
                const uint8_t blue = bytes[offset + 2];
                pixels[(size_t)(y * imageWidth + x)] =
                    (jint)(0xFF000000u | ((uint32_t)red << 16) |
                           ((uint32_t)green << 8) | (uint32_t)blue);
            }
        }

        javaPixels = env->NewIntArray((jsize)pixels.size());
        if (!javaPixels) {
            set_error("Não há memória suficiente para receber a imagem.");
            goto cleanup;
        }
        env->SetIntArrayRegion(javaPixels, 0, (jsize)pixels.size(), pixels.data());
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            javaPixels = nullptr;
            set_error("Não foi possível transferir a imagem para o aplicativo.");
            goto cleanup;
        }
    }

cleanup:
    if (context) sd_set_generation_progress_callback(context, nullptr, nullptr);
    sd_result_free(result);
    sd_opt_free(imageOptions);
    sd_opt_free(contextOptions);
    return javaPixels;
}

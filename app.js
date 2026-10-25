(() => {
  "use strict";
  const $ = (s) => document.querySelector(s);
  const $$ = (s) => [...document.querySelectorAll(s)];
  const STORE = {tasks:"aurora_tasks_v1", memories:"aurora_memories_v1", messages:"aurora_messages_v1", settings:"aurora_settings_v1"};
  const read = (key, fallback) => { try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; } };
  const write = (key, value) => localStorage.setItem(key, JSON.stringify(value));
  let tasks = read(STORE.tasks, []);
  let memories = read(STORE.memories, []);
  let history = read(STORE.messages, []);
  let settings = read(STORE.settings, {speak:true, voice:true, voiceName:"auto"});
  settings.voiceName = settings.voiceName || "auto";
  let toastTimer;

  function toast(msg) {
    const el = $("#toast"); el.textContent = msg; el.classList.add("show");
    clearTimeout(toastTimer); toastTimer = setTimeout(() => el.classList.remove("show"), 2600);
  }
  function safeText(el, text) { el.textContent = text; }
  function switchView(id) {
    $$(".view").forEach(v => v.classList.toggle("active", v.id === id));
    $$(".nav-item").forEach(v => v.classList.toggle("selected", v.dataset.view === id));
    $("#composer").classList.toggle("hidden", id !== "chatView");
    if (id === "tasksView") renderTasks();
    if (id === "memoryView") renderMemories();
    window.scrollTo({top:0, behavior:"smooth"});
  }
  $$(".nav-item").forEach(btn => btn.addEventListener("click", () => switchView(btn.dataset.view)));
  $("#settingsBtn").addEventListener("click", () => {
    $("#speakToggle").checked = settings.speak;
    $("#voiceToggle").checked = settings.voice;
    populateVoiceSelect();
    $("#voiceSelect").value = settings.voiceName || "auto";
    $("#settingsDialog").showModal();
  });
  $("#voiceSelect").addEventListener("change", e => {
    settings.voiceName = e.target.value || "auto";
    write(STORE.settings, settings);
    toast("Voz da Aurora atualizada.");
  });
  $("#speakToggle").addEventListener("change", e => {settings.speak=e.target.checked;write(STORE.settings,settings);});
  $("#voiceToggle").addEventListener("change", e => {settings.voice=e.target.checked;write(STORE.settings,settings);});
  $("#clearBtn").addEventListener("click", () => {
    if (confirm("Apagar todas as conversas, tarefas e memórias deste aparelho? Esta ação não pode ser desfeita.")) {
      Object.values(STORE).forEach(k => localStorage.removeItem(k));
      tasks=[];memories=[];history=[];settings={speak:true,voice:true};
      renderTasks();renderMemories();renderMessages();$("#settingsDialog").close();toast("Dados locais apagados.");
    }
  });

  function renderMessages() {
    const box=$("#messages"); box.replaceChildren();
    history.slice(-80).forEach(m => {
      const el=document.createElement("div");el.className="message "+(m.role==="user"?"user":"assistant");
      if(m.role!=="user"){const who=document.createElement("span");who.className="sender";who.textContent="AURORA";el.append(who);}
      const content=document.createElement("div");content.textContent=m.text;el.append(content);box.append(el);
    });
    box.scrollTop=box.scrollHeight;
  }
  function addMessage(role,text) {
    history.push({role,text,at:Date.now()}); history=history.slice(-100); write(STORE.messages,history); renderMessages();
  }
  function renderTasks() {
    const box=$("#taskList");box.replaceChildren();
    if(!tasks.length){const e=document.createElement("div");e.className="empty";e.textContent="Nenhuma tarefa por enquanto. Adiciona a primeira e vamos nessa!";box.append(e);return;}
    tasks.forEach(t=>{
      const row=document.createElement("div");row.className="task-item"+(t.done?" done":"");
      const check=document.createElement("input");check.type="checkbox";check.checked=t.done;check.setAttribute("aria-label","Concluir tarefa");
      check.addEventListener("change",()=>{t.done=check.checked;write(STORE.tasks,tasks);renderTasks();});
      const label=document.createElement("span");label.className="task-text";label.textContent=t.text;
      const del=document.createElement("button");del.className="delete-btn";del.textContent="×";del.setAttribute("aria-label","Excluir tarefa");
      del.addEventListener("click",()=>{tasks=tasks.filter(x=>x.id!==t.id);write(STORE.tasks,tasks);renderTasks();toast("Tarefa removida.");});
      row.append(check,label,del);box.append(row);
    });
  }
  function renderMemories() {
    const box=$("#memoryList");box.replaceChildren();
    if(!memories.length){const e=document.createElement("div");e.className="empty";e.textContent="Ainda não guardei nada. Diz “lembre que...” no chat ou escreve uma memória acima.";box.append(e);return;}
    memories.slice().reverse().forEach(m=>{
      const row=document.createElement("div");row.className="memory-item";
      const p=document.createElement("p");p.textContent=m.text;
      const del=document.createElement("button");del.className="delete-btn";del.textContent="×";del.setAttribute("aria-label","Excluir memória");
      del.addEventListener("click",()=>{memories=memories.filter(x=>x.id!==m.id);write(STORE.memories,memories);renderMemories();toast("Memória apagada.");});
      row.append(p,del);box.append(row);
    });
  }
  function addTask(text) {
    const clean=text.trim();if(!clean)return false;
    tasks.push({id:Date.now()+Math.random(),text:clean,done:false});write(STORE.tasks,tasks);renderTasks();return true;
  }
  function addMemory(text) {
    const clean=text.trim();if(!clean)return false;
    memories.push({id:Date.now()+Math.random(),text:clean});write(STORE.memories,memories);renderMemories();return true;
  }
  $("#taskForm").addEventListener("submit",e=>{e.preventDefault();if(addTask($("#taskInput").value)){toast("Tarefa adicionada.");$("#taskInput").value="";}});
  $("#memoryForm").addEventListener("submit",e=>{e.preventDefault();if(addMemory($("#memoryInput").value)){toast("Memória guardada neste aparelho.");$("#memoryInput").value="";}});

  const HELP = "Posso ajudar com comandos locais:\n• “lembre que prefiro respostas curtas” — guarda uma memória\n• “o que você lembra?” — lista memórias salvas\n• “adiciona tarefa estudar matemática” — cria uma tarefa\n• “minhas tarefas” — mostra as tarefas\n• “concluir tarefa 1” — conclui a primeira tarefa pendente\n• “que horas são?” — mostra data e hora\n• “limpar conversa” — apaga apenas o histórico de chat\n• “ajuda” — mostra esta lista\n\nNesta versão gratuita, eu não tenho conhecimento geral ilimitado como um modelo de IA conectado à internet.";
  function normalize(s){return s.toLocaleLowerCase("pt-BR").normalize("NFD").replace(/[\u0300-\u036f]/g,"").trim();}
  function localReply(input) {
    const n=normalize(input);
    if(/^(oi|ola|e ai|bom dia|boa tarde|boa noite|tudo bem)[!. ]*$/.test(n)) return "Oi! ✨ Estou aqui contigo. Quer conversar, guardar alguma coisa ou organizar uma tarefa?";
    if(n.includes("ajuda")||n.includes("o que voce consegue fazer")||n==="comandos")return HELP;
    if(n.includes("que horas")||n.includes("data de hoje")||n==="data"||n==="hora")return "Agora são "+new Intl.DateTimeFormat("pt-BR",{dateStyle:"full",timeStyle:"short"}).format(new Date())+".";
    if(n.includes("o que voce lembra")||n.includes("minhas memorias")||n.includes("minha memoria")||n.includes("listar memorias")) {
      return memories.length ? "Estas são as memórias que guardei:\n"+memories.map((m,i)=>`${i+1}. ${m.text}`).join("\n") : "Ainda não tenho memórias salvas. Podes dizer “lembre que...” seguido da informação.";
    }
    if(n.startsWith("lembre que ")||n.startsWith("lembra que ")||n.startsWith("guarde que ")||n.startsWith("guarda que ")||n.startsWith("memorize ")) {
      const content=input.trim().replace(/^(lembre que|lembra que|guarde que|guarda que|memorize)\s*/i,"");
      if(addMemory(content))return "Pronto! Guardei isso na minha memória local:\n“"+content+"”\n\nPodes ver ou apagar memórias na aba Memória.";
      return "Diz-me qual informação queres guardar.";
    }
    const taskMatch=input.match(/^(?:adiciona|adicionar|cria|criar|nova|anota|coloca)\s+(?:uma\s+)?tarefa\s*[:\-]?\s*(.+)$/i);
    if(taskMatch){addTask(taskMatch[1]);return "Tarefa adicionada: “"+taskMatch[1].trim()+"”. Podes conferir na aba Tarefas.";}
    if(n.includes("minhas tarefas")||n.includes("lista de tarefas")||n==="tarefas") {
      const pending=tasks.filter(t=>!t.done);
      return tasks.length ? "Tuas tarefas:\n"+tasks.map((t,i)=>`${i+1}. ${t.done?"✓ ":""}${t.text}`).join("\n")+"\n\nPendentes: "+pending.length : "Ainda não há tarefas. Diz “adiciona tarefa” e o que precisas fazer.";
    }
    const doneMatch=n.match(/concluir tarefa\s*(\d+)/);
    if(doneMatch){const idx=Number(doneMatch[1])-1;const pending=tasks.filter(t=>!t.done);if(pending[idx]){pending[idx].done=true;write(STORE.tasks,tasks);renderTasks();return "Feito! Marquei como concluída: “"+pending[idx].text+"”.";}return "Não encontrei uma tarefa pendente com esse número.";}
    if(n.includes("limpar conversa")){history=[];write(STORE.messages,history);renderMessages();return "Histórico da conversa apagado. Suas tarefas e memórias continuam salvas.";}
    if(n.includes("organizar meu dia")) {
      const pending=tasks.filter(t=>!t.done);
      return pending.length ? "Vamos organizar com calma. Tens "+pending.length+" tarefa(s) pendente(s):\n"+pending.map((t,i)=>`${i+1}. ${t.text}`).join("\n")+"\n\nEscolhe uma importante para começar e divide tarefas grandes em passos pequenos." : "Vamos começar simples: escolhe uma coisa importante, uma coisa rápida e uma pausa. Se quiseres, adiciona tarefas na aba Tarefas.";
    }
    if(n.includes("obrigad"))return "Por nada! 💜 Estou aqui para te ajudar com as ferramentas que tenho nesta versão.";
    if(n.includes("quem e voce")||n.includes("seu nome"))return "Sou a Aurora, tua assistente pessoal. Nesta versão posso guardar memórias, organizar tarefas, falar respostas e executar comandos locais.";
    return "Ainda não tenho um modelo de linguagem conectado para responder livremente a isso. Posso, no entanto, ajudar com tarefas, memórias, data e hora. Escreve “ajuda” para ver os comandos disponíveis.\n\nQuando quiseres, podemos preparar uma conexão opcional com um modelo de IA — alguns serviços oferecem cotas gratuitas, mas dependem das regras e disponibilidade do fornecedor.";
  }
  function availablePortugueseVoices() {
    if (!("speechSynthesis" in window)) return [];
    return window.speechSynthesis.getVoices().filter(v => /^pt(-|_)/i.test(v.lang));
  }
  function recommendedVoice(voices) {
    const br = voices.filter(v => /^pt[-_]br/i.test(v.lang));
    const pool = br.length ? br : voices;
    const feminineHints = /female|feminina|francisca|luciana|camila|fernanda|mariana|vitoria|vit[oó]ria|joana|google português do brasil/i;
    return pool.find(v => feminineHints.test(v.name)) || pool.find(v => /google/i.test(v.name)) || pool[0] || null;
  }
  function populateVoiceSelect() {
    const select = $("#voiceSelect");
    if (!select || !("speechSynthesis" in window)) return;
    const voices = availablePortugueseVoices();
    const current = settings.voiceName || "auto";
    select.replaceChildren();
    const auto = document.createElement("option");
    auto.value = "auto"; auto.textContent = "Voz brasileira recomendada";
    select.append(auto);
    voices.forEach(v => {
      const option = document.createElement("option");
      option.value = v.name;
      option.textContent = v.name + " (" + v.lang + ")";
      select.append(option);
    });
    select.value = current;
    if (select.value !== current) select.value = "auto";
  }
  if ("speechSynthesis" in window) {
    window.speechSynthesis.addEventListener("voiceschanged", populateVoiceSelect);
    populateVoiceSelect();
  }
  function speak(text) {
    if (!settings.speak || !("speechSynthesis" in window)) return;
    window.speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(text);
    u.lang = "pt-BR";
    // Slightly slower and warmer delivery for Aurora's conversational personality.
    u.rate = 0.94;
    u.pitch = 1.12;
    const voices = availablePortugueseVoices();
    const preferred = settings.voiceName && settings.voiceName !== "auto"
      ? voices.find(v => v.name === settings.voiceName)
      : recommendedVoice(voices);
    if (preferred) { u.voice = preferred; u.lang = preferred.lang; }
    window.speechSynthesis.speak(u);
  }
  function send(text) {
    const value=text.trim();if(!value)return;
    addMessage("user",value);
    const response=localReply(value);
    addMessage("assistant",response);speak(response);
  }
  $("#composer").addEventListener("submit",e=>{e.preventDefault();const input=$("#chatInput");send(input.value);input.value="";input.style.height="auto";});
  $("#chatInput").addEventListener("input",e=>{e.target.style.height="auto";e.target.style.height=Math.min(e.target.scrollHeight,110)+"px";});
  $$(".suggestion").forEach(b=>b.addEventListener("click",()=>send(b.dataset.prompt)));
  $$(".tool-card").forEach(b=>b.addEventListener("click",()=>{
    const a=b.dataset.action;
    if(a==="task"){switchView("tasksView");$("#taskInput").focus();}
    else if(a==="memory"){switchView("memoryView");$("#memoryInput").focus();}
    else if(a==="time"){switchView("chatView");send("Que horas são?");}
    else {switchView("chatView");send("ajuda");}
  }));

  const SpeechRecognition=window.SpeechRecognition||window.webkitSpeechRecognition;
  let recognition=null, listening=false;
  const voiceBtn=$("#voiceBtn");
  if(SpeechRecognition){
    recognition=new SpeechRecognition();recognition.lang="pt-BR";recognition.interimResults=false;recognition.maxAlternatives=1;
    recognition.onstart=()=>{listening=true;voiceBtn.classList.add("listening");voiceBtn.textContent="●";toast("Estou ouvindo…");};
    recognition.onend=()=>{listening=false;voiceBtn.classList.remove("listening");voiceBtn.textContent="🎙";};
    recognition.onerror=e=>toast(e.error==="not-allowed"?"Permite o microfone nas configurações do navegador.":"Não consegui ouvir. Tenta novamente.");
    recognition.onresult=e=>{const transcript=e.results[0][0].transcript;$("#chatInput").value=transcript;send(transcript);$("#chatInput").value="";};
    voiceBtn.addEventListener("click",()=>{if(!settings.voice){toast("Ativa o microfone nas configurações.");return;}try{if(listening)recognition.stop();else recognition.start();}catch{toast("O microfone já está ativo.");}});
  }else{
    voiceBtn.addEventListener("click",()=>toast("O reconhecimento de voz não é suportado neste navegador. Tenta abrir pelo Google Chrome no Android."));
  }

  let deferredPrompt=null;
  window.addEventListener("beforeinstallprompt",e=>{e.preventDefault();deferredPrompt=e;$("#installBtn").classList.remove("hidden");});
  $("#installBtn").addEventListener("click",async()=>{if(!deferredPrompt)return;deferredPrompt.prompt();await deferredPrompt.userChoice;deferredPrompt=null;$("#installBtn").classList.add("hidden");});
  if("serviceWorker" in navigator && location.protocol.startsWith("http"))window.addEventListener("load",()=>navigator.serviceWorker.register("./sw.js").catch(()=>{}));
  renderMessages();renderTasks();renderMemories();
  if(!history.length){addMessage("assistant","Oi! Eu sou a Aurora ✨\n\nPosso guardar memórias e organizar tarefas neste aparelho. Experimenta dizer “ajuda” para ver os comandos disponíveis.");}
})();

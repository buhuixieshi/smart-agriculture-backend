const state = {
  page: localStorage.getItem("sa_page") || "dashboard",
  apiBase: localStorage.getItem("sa_api_base") || "http://localhost:8080",
  token: localStorage.getItem("sa_token") || "",
  user: JSON.parse(localStorage.getItem("sa_user") || "null"),
  plots: [],
  devices: []
};

const pages = [
  ["dashboard", "总览", "查看地块、设备、遥测和告警概况。"],
  ["auth", "登录注册", "注册、登录、查看当前用户。"],
  ["plots", "地块管理", "新增、修改、删除地块。"],
  ["devices", "设备管理", "新增设备、绑定地块、启停设备。"],
  ["telemetry", "遥测数据", "查看实时与历史传感器数据，模拟硬件上报。"],
  ["control", "设备控制", "控制水泵、补光灯，查看控制命令。"],
  ["strategies", "策略配置", "配置自动灌溉和智能补光策略。"],
  ["water", "用水限制", "配置灌溉用水上限和提醒阈值。"],
  ["alarms", "告警管理", "查看、确认、关闭、恢复告警。"],
  ["logs", "操作日志", "查看后端操作记录。"],
  ["ai", "AI 助手", "智能农事问答、页面跳转和动作建议。"],
  ["pest", "害虫识别", "上传图片识别害虫并查看记录。"]
];

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

function init() {
  $("#apiBase").value = state.apiBase;
  renderNav();
  bindGlobalEvents();
  renderPage();
}

function bindGlobalEvents() {
  $("#apiBase").addEventListener("change", (event) => {
    state.apiBase = event.target.value.trim() || "http://localhost:8080";
    localStorage.setItem("sa_api_base", state.apiBase);
    toast(`后端地址已切换为 ${state.apiBase}`);
  });

  $("#healthBtn").addEventListener("click", async () => {
    try {
      const data = await api("/api/health");
      toast(`后端健康检查成功：${JSON.stringify(data)}`);
    } catch (error) {
      toast(error.message, true);
    }
  });

  $("#refreshBtn").addEventListener("click", () => renderPage());

  $("#content").addEventListener("click", async (event) => {
    const target = event.target.closest("[data-action]");
    if (!target) return;
    const action = target.dataset.action;
    const id = target.dataset.id;
    try {
      await handleAction(action, id, target);
    } catch (error) {
      toast(error.message, true);
    }
  });
}

function renderNav() {
  $("#nav").innerHTML = pages.map(([key, title]) => `
    <button class="nav-btn ${state.page === key ? "active" : ""}" data-page="${key}">${title}</button>
  `).join("");

  $$(".nav-btn").forEach((button) => {
    button.addEventListener("click", () => {
      state.page = button.dataset.page;
      localStorage.setItem("sa_page", state.page);
      renderNav();
      renderPage();
    });
  });
}

async function renderPage() {
  const config = pages.find(([key]) => key === state.page) || pages[0];
  $("#pageTitle").textContent = config[1];
  $("#pageDesc").textContent = config[2];
  $("#content").innerHTML = "";
  $("#content").appendChild($(`#${state.page}Tpl`).content.cloneNode(true));

  try {
    if (["dashboard", "plots", "devices", "telemetry", "control", "strategies", "water", "alarms", "logs", "ai", "pest"].includes(state.page)) {
      await loadCommonData();
    }

    const loaders = {
      dashboard: renderDashboard,
      auth: renderAuth,
      plots: renderPlots,
      devices: renderDevices,
      telemetry: renderTelemetry,
      control: renderControl,
      strategies: renderStrategies,
      water: renderWater,
      alarms: renderAlarms,
      logs: renderLogs,
      ai: renderAi,
      pest: renderPest
    };
    await loaders[state.page]();
  } catch (error) {
    toast(error.message, true);
  }
}

async function loadCommonData() {
  const [plots, devices] = await Promise.all([
    api("/api/plots").catch(() => []),
    api("/api/devices").catch(() => [])
  ]);
  state.plots = Array.isArray(plots) ? plots : [];
  state.devices = normalizeList(devices);
}

function normalizeList(value) {
  if (Array.isArray(value)) return value;
  if (value && Array.isArray(value.records)) return value.records;
  return [];
}

async function renderAuth() {
  $("#authState").textContent = JSON.stringify({
    apiBase: state.apiBase,
    loggedIn: Boolean(state.token),
    user: state.user,
    tokenPreview: state.token ? `${state.token.slice(0, 16)}...` : ""
  }, null, 2);
}

async function renderDashboard() {
  const activeAlarms = await api("/api/alarms?status=ACTIVE").catch(() => []);
  $("#stats").innerHTML = [
    stat("地块数", state.plots.length),
    stat("设备数", state.devices.length),
    stat("在线设备", state.devices.filter((item) => item.status === "ONLINE").length),
    stat("活跃告警", activeAlarms.length)
  ].join("");

  fillPlotSelect($("#dashboardPlot"), true);
  $("#dashboardPlot").addEventListener("change", loadDashboardTelemetry);
  await loadDashboardTelemetry();

  $("#deviceSummary").innerHTML = table(state.devices, [
    ["id", "ID"],
    ["deviceCode", "设备编号"],
    ["deviceName", "名称"],
    ["plotId", "地块"],
    ["status", "状态"]
  ]);

  $("#activeAlarms").innerHTML = table(activeAlarms, [
    ["id", "ID"],
    ["plotId", "地块"],
    ["deviceId", "设备"],
    ["alarmType", "类型"],
    ["severity", "级别"],
    ["message", "消息"],
    ["createTime", "时间"]
  ]);
}

async function loadDashboardTelemetry() {
  const plotId = $("#dashboardPlot").value || firstPlotId();
  if (!plotId) {
    $("#latestTelemetry").innerHTML = empty("暂无地块");
    return;
  }
  const latest = await api(`/api/telemetry/latest?plotId=${encodeURIComponent(plotId)}`).catch(() => null);
  $("#latestTelemetry").innerHTML = telemetryMetrics(latest);
}

async function renderPlots() {
  $("#plotsTable").innerHTML = table(state.plots, [
    ["id", "ID"],
    ["name", "名称"],
    ["cropType", "作物"],
    ["location", "位置"],
    ["area", "面积"],
    ["status", "状态"],
    ["description", "描述"]
  ], (item) => `<button class="btn secondary" data-action="editPlot" data-id="${item.id}">编辑</button>`);
}

async function renderDevices() {
  fillPlotSelect($("#devicePlotId"), true);
  $("#devicesTable").innerHTML = table(state.devices, [
    ["id", "ID"],
    ["deviceCode", "设备编号"],
    ["deviceName", "名称"],
    ["deviceType", "类型"],
    ["plotId", "地块"],
    ["status", "状态"],
    ["lastHeartbeat", "心跳"],
    ["battery", "电量"]
  ], (item) => `<button class="btn secondary" data-action="editDevice" data-id="${item.id}">编辑</button>`);
}

async function renderTelemetry() {
  fillPlotSelect($("#telemetryPlot"), true);
  await loadTelemetry();
}

async function loadTelemetry() {
  const plotId = $("#telemetryPlot").value || firstPlotId();
  const deviceId = $("#telemetryDeviceId").value.trim();
  const latest = plotId ? await api(`/api/telemetry/latest?plotId=${encodeURIComponent(plotId)}`).catch(() => null) : null;
  $("#telemetryLatest").innerHTML = telemetryMetrics(latest);

  const query = new URLSearchParams({ page: "1", size: "30" });
  if (plotId) query.set("plotId", plotId);
  if (deviceId) query.set("deviceId", deviceId);
  const history = await api(`/api/telemetry/history?${query}`).catch(() => ({ records: [] }));
  $("#telemetryHistory").innerHTML = table(normalizeList(history), [
    ["id", "ID"],
    ["plotId", "地块"],
    ["deviceId", "设备"],
    ["deviceCode", "设备编号"],
    ["soilMoisture", "土壤湿度"],
    ["airTemperature", "温度"],
    ["airHumidity", "空气湿度"],
    ["illuminance", "照度"],
    ["pumpStatus", "水泵"],
    ["lightStatus", "补光"],
    ["collectedAt", "采集时间"]
  ]);
}

async function renderControl() {
  fillDeviceSelect($("#controlDevice"));
  fillDeviceSelect($("#lightDevice"));
  const first = state.devices[0];
  if (first) $("#commandDeviceCode").value = first.deviceCode || "";
}

async function renderStrategies() {
  fillPlotSelect($("#strategyPlot"));
  fillPlotSelect($("#lightStrategyPlot"));
  $("#strategyPlot").addEventListener("change", loadIrrigationStrategy);
  $("#lightStrategyPlot").addEventListener("change", loadLightStrategy);
  await loadIrrigationStrategy();
  await loadLightStrategy();
}

async function renderWater() {
  fillPlotSelect($("#waterPlot"));
  $("#waterPlot").addEventListener("change", loadWaterLimit);
  await loadWaterLimit();
  await loadWaterLimits();
}

async function renderAlarms() {
  fillPlotSelect($("#alarmPlot"), true);
  await loadAlarms();
}

async function renderLogs() {
  await loadLogs();
}

async function renderAi() {
  fillPlotSelect($("#aiPlot"), true);
  fillAiDeviceSelect();
  $("#aiPlot").addEventListener("change", fillAiDeviceSelect);
  $("#aiAnswer").textContent = "可以问：当前地块需要灌溉吗？打开水泵。跳转到设备管理页面。";
}

async function renderPest() {
  fillPlotSelect($("#pestPlot"), true);
  await loadPestRecords();
}

async function handleAction(action, id, target) {
  const handlers = {
    login,
    logout,
    register,
    savePlot,
    deletePlot,
    clearPlotForm,
    editPlot: () => editPlot(id),
    saveDevice,
    deleteDevice,
    editDevice: () => editDevice(id),
    bindDevice,
    unbindDevice,
    enableDevice,
    disableDevice,
    loadTelemetry,
    simulateReport,
    sendPump,
    sendLight,
    loadCommands,
    loadCommandStatus,
    saveIrrigationStrategy,
    saveLightStrategy,
    saveWaterLimit,
    loadAlarms,
    loadLogs,
    ackAlarm: () => alarmAction(id, "ack"),
    closeAlarm: () => alarmAction(id, "close"),
    recoverAlarm: () => alarmAction(id, "recover"),
    askAi,
    detectPest
  };
  const handler = handlers[action];
  if (!handler) {
    toast(`未实现动作：${action}`, true);
    return;
  }
  target.disabled = true;
  try {
    await handler();
  } finally {
    target.disabled = false;
  }
}

async function login() {
  const username = $("#loginUsername").value.trim();
  const password = $("#loginPassword").value;
  const data = await api("/api/auth/login", {
    method: "POST",
    body: { username, password }
  });
  state.token = data.token;
  state.user = data;
  localStorage.setItem("sa_token", state.token);
  localStorage.setItem("sa_user", JSON.stringify(state.user));
  toast("登录成功");
  await renderAuth();
}

async function logout() {
  state.token = "";
  state.user = null;
  localStorage.removeItem("sa_token");
  localStorage.removeItem("sa_user");
  toast("已退出登录");
  await renderAuth();
}

async function register() {
  const body = {
    username: $("#registerUsername").value.trim(),
    nickname: $("#registerNickname").value.trim(),
    password: $("#registerPassword").value
  };
  await api("/api/auth/register", { method: "POST", body });
  toast("注册成功，可以登录了");
}

async function savePlot() {
  const id = $("#plotId").value.trim();
  const body = {
    name: $("#plotName").value.trim(),
    cropType: $("#plotCropType").value.trim(),
    location: $("#plotLocation").value.trim(),
    area: numberOrNull($("#plotArea").value),
    status: $("#plotStatus").value.trim(),
    description: $("#plotDescription").value.trim()
  };
  await api(id ? `/api/plots/${id}` : "/api/plots", {
    method: id ? "PUT" : "POST",
    body
  });
  toast("地块已保存");
  await renderPage();
}

async function deletePlot() {
  const id = $("#plotId").value.trim();
  if (!id || !confirm(`确认删除地块 ${id}？`)) return;
  await api(`/api/plots/${id}`, { method: "DELETE" });
  toast("地块已删除");
  await renderPage();
}

function editPlot(id) {
  const item = state.plots.find((plot) => String(plot.id) === String(id));
  if (!item) return;
  $("#plotId").value = item.id ?? "";
  $("#plotName").value = item.name ?? "";
  $("#plotCropType").value = item.cropType ?? "";
  $("#plotLocation").value = item.location ?? "";
  $("#plotArea").value = item.area ?? "";
  $("#plotStatus").value = item.status ?? "";
  $("#plotDescription").value = item.description ?? "";
}

function clearPlotForm() {
  ["plotId", "plotName", "plotCropType", "plotLocation", "plotArea", "plotStatus", "plotDescription"]
    .forEach((id) => $("#" + id).value = "");
}

async function saveDevice() {
  const id = $("#deviceId").value.trim();
  const body = {
    plotId: numberOrNull($("#devicePlotId").value),
    deviceCode: $("#deviceCode").value.trim(),
    deviceName: $("#deviceName").value.trim(),
    deviceType: $("#deviceType").value.trim(),
    status: $("#deviceStatus").value.trim(),
    signalStrength: intOrNull($("#deviceSignal").value),
    battery: intOrNull($("#deviceBattery").value)
  };
  await api(id ? `/api/devices/${id}` : "/api/devices", {
    method: id ? "PUT" : "POST",
    body
  });
  toast("设备已保存");
  await renderPage();
}

async function deleteDevice() {
  const id = $("#deviceId").value.trim();
  if (!id || !confirm(`确认删除设备 ${id}？`)) return;
  await api(`/api/devices/${id}`, { method: "DELETE" });
  toast("设备已删除");
  await renderPage();
}

function editDevice(id) {
  const item = state.devices.find((device) => String(device.id) === String(id));
  if (!item) return;
  $("#deviceId").value = item.id ?? "";
  $("#deviceCode").value = item.deviceCode ?? "";
  $("#deviceName").value = item.deviceName ?? "";
  $("#deviceType").value = item.deviceType ?? "";
  $("#devicePlotId").value = item.plotId ?? "";
  $("#deviceStatus").value = item.status ?? "";
  $("#deviceSignal").value = item.signalStrength ?? "";
  $("#deviceBattery").value = item.battery ?? "";
}

async function bindDevice() {
  const id = $("#deviceId").value.trim();
  const plotId = $("#devicePlotId").value;
  if (!id || !plotId) throw new Error("请填写设备 ID 和地块");
  await api(`/api/devices/${id}/bind?plotId=${encodeURIComponent(plotId)}`, { method: "PUT" });
  toast("设备已绑定地块");
  await renderPage();
}

async function unbindDevice() {
  const id = $("#deviceId").value.trim();
  if (!id) throw new Error("请填写设备 ID");
  await api(`/api/devices/${id}/unbind`, { method: "PUT" });
  toast("设备已解绑");
  await renderPage();
}

async function enableDevice() {
  const id = $("#deviceId").value.trim();
  if (!id) throw new Error("请填写设备 ID");
  await api(`/api/devices/${id}/enable`, { method: "PUT" });
  toast("设备已启用");
  await renderPage();
}

async function disableDevice() {
  const id = $("#deviceId").value.trim();
  if (!id) throw new Error("请填写设备 ID");
  await api(`/api/devices/${id}/disable`, { method: "PUT" });
  toast("设备已停用");
  await renderPage();
}

async function simulateReport() {
  const device = deviceByPlot($("#telemetryPlot").value) || state.devices[0];
  if (!device) throw new Error("没有可用设备，先创建设备");
  const body = {
    deviceCode: device.deviceCode,
    soilMoisture: random(25, 70),
    airTemperature: random(20, 34),
    airHumidity: random(45, 75),
    illuminance: random(250, 700),
    pumpStatus: "OFF",
    lightStatus: "OFF"
  };
  const data = await api("/api/iotda/report", { method: "POST", body, rawResult: true });
  toast(`模拟上报成功：${JSON.stringify(data)}`);
  await loadTelemetry();
}

async function sendPump() {
  const deviceCode = $("#controlDevice").value;
  const commandType = $("#pumpAction").value;
  const query = new URLSearchParams({
    deviceCode,
    commandType,
    commandValue: commandType === "PUMP_ON" ? "ON" : "OFF"
  });
  const duration = $("#durationSeconds").value.trim();
  if (duration) query.set("durationSeconds", duration);
  const data = await api(`/api/control/send?${query}`, { method: "POST" });
  toast(`水泵命令已发送：${data.commandNo || data.id}`);
}

async function sendLight() {
  const body = {
    deviceCode: $("#lightDevice").value,
    action: $("#lightAction").value,
    brightness: intOrNull($("#lightBrightness").value)
  };
  const data = await api("/api/light/control", { method: "POST", body });
  toast(`补光命令已发送：${data.commandNo || data.id}`);
}

async function loadCommands() {
  const deviceCode = $("#commandDeviceCode").value.trim();
  if (!deviceCode) throw new Error("请输入 deviceCode");
  const data = await api(`/api/control/list?deviceCode=${encodeURIComponent(deviceCode)}`);
  $("#commandsTable").innerHTML = table(data, [
    ["id", "ID"],
    ["commandNo", "命令号"],
    ["deviceCode", "设备"],
    ["commandType", "类型"],
    ["commandValue", "值"],
    ["durationSeconds", "秒数"],
    ["brightness", "亮度"],
    ["status", "状态"],
    ["errorMessage", "消息"],
    ["createdAt", "创建时间"]
  ]);
}

async function loadCommandStatus() {
  const commandNo = $("#commandNo").value.trim();
  if (!commandNo) throw new Error("请输入 commandNo");
  const data = await api(`/api/control/commands/${encodeURIComponent(commandNo)}`);
  $("#commandsTable").innerHTML = `<pre>${escapeHtml(JSON.stringify(data, null, 2))}</pre>`;
}

async function loadIrrigationStrategy() {
  const plotId = $("#strategyPlot").value || firstPlotId();
  if (!plotId) return;
  const data = await api(`/api/strategies/${plotId}`);
  $("#moistureMin").value = data.moistureMin ?? "";
  $("#moistureMax").value = data.moistureMax ?? "";
  $("#consecutiveThreshold").value = data.consecutiveThreshold ?? "";
  $("#maxDuration").value = data.maxDuration ?? "";
  $("#cooldownMinutes").value = data.cooldownMinutes ?? "";
  $("#irrigationAuto").checked = Boolean(data.autoMode);
}

async function saveIrrigationStrategy() {
  const plotId = $("#strategyPlot").value;
  if (!plotId) throw new Error("请选择地块");
  const body = {
    moistureMin: numberOrNull($("#moistureMin").value),
    moistureMax: numberOrNull($("#moistureMax").value),
    consecutiveThreshold: intOrNull($("#consecutiveThreshold").value),
    autoMode: $("#irrigationAuto").checked,
    maxDuration: intOrNull($("#maxDuration").value),
    cooldownMinutes: intOrNull($("#cooldownMinutes").value)
  };
  await api(`/api/strategies/${plotId}`, { method: "PUT", body });
  toast("灌溉策略已保存");
}

async function loadLightStrategy() {
  const plotId = $("#lightStrategyPlot").value || firstPlotId();
  if (!plotId) return;
  const data = await api(`/api/light-strategies/${plotId}`);
  $("#illuminanceMin").value = data.illuminanceMin ?? "";
  $("#illuminanceMax").value = data.illuminanceMax ?? "";
  $("#lightStartTime").value = trimTime(data.startTime);
  $("#lightEndTime").value = trimTime(data.endTime);
  $("#lightCooldown").value = data.cooldownMinutes ?? "";
  $("#lightAuto").checked = Boolean(data.autoMode);
}

async function saveLightStrategy() {
  const plotId = $("#lightStrategyPlot").value;
  if (!plotId) throw new Error("请选择地块");
  const body = {
    illuminanceMin: numberOrNull($("#illuminanceMin").value),
    illuminanceMax: numberOrNull($("#illuminanceMax").value),
    autoMode: $("#lightAuto").checked,
    startTime: $("#lightStartTime").value || null,
    endTime: $("#lightEndTime").value || null,
    cooldownMinutes: intOrNull($("#lightCooldown").value)
  };
  await api(`/api/light-strategies/${plotId}`, { method: "PUT", body });
  toast("补光策略已保存");
}

async function loadWaterLimit() {
  const plotId = $("#waterPlot").value || firstPlotId();
  if (!plotId) return;
  const data = await api(`/api/water-limits/${plotId}`);
  $("#dailyLimit").value = data.dailyLimit ?? "";
  $("#monthlyLimit").value = data.monthlyLimit ?? "";
  $("#singleLimit").value = data.singleLimit ?? "";
  $("#alertPercent").value = data.alertPercent ?? "";
  $("#minEffectiveDuration").value = data.minEffectiveDuration ?? "";
}

async function loadWaterLimits() {
  const data = await api("/api/water-limits").catch(() => []);
  $("#waterLimitsTable").innerHTML = table(data, [
    ["id", "ID"],
    ["plotId", "地块"],
    ["dailyLimit", "单日上限"],
    ["monthlyLimit", "单月上限"],
    ["singleLimit", "单次上限"],
    ["alertPercent", "提醒百分比"],
    ["minEffectiveDuration", "最低有效秒数"]
  ]);
}

async function saveWaterLimit() {
  const plotId = $("#waterPlot").value;
  if (!plotId) throw new Error("请选择地块");
  const body = {
    dailyLimit: numberOrNull($("#dailyLimit").value),
    monthlyLimit: numberOrNull($("#monthlyLimit").value),
    singleLimit: numberOrNull($("#singleLimit").value),
    alertPercent: numberOrNull($("#alertPercent").value),
    minEffectiveDuration: intOrNull($("#minEffectiveDuration").value)
  };
  await api(`/api/water-limits/${plotId}`, { method: "PUT", body });
  toast("用水限制已保存");
  await loadWaterLimits();
}

async function loadAlarms() {
  const query = new URLSearchParams();
  const plotId = $("#alarmPlot").value;
  const status = $("#alarmStatus").value;
  const alarmType = $("#alarmType").value.trim();
  if (plotId) query.set("plotId", plotId);
  if (status) query.set("status", status);
  if (alarmType) query.set("alarmType", alarmType);
  const data = await api(`/api/alarms?${query}`);
  $("#alarmsTable").innerHTML = table(data, [
    ["id", "ID"],
    ["plotId", "地块"],
    ["deviceId", "设备"],
    ["alarmType", "类型"],
    ["severity", "级别"],
    ["triggerValue", "触发值"],
    ["thresholdValue", "阈值"],
    ["status", "状态"],
    ["message", "消息"],
    ["createTime", "创建时间"]
  ], (item) => `
    <button class="btn secondary" data-action="ackAlarm" data-id="${item.id}">确认</button>
    <button class="btn secondary" data-action="recoverAlarm" data-id="${item.id}">恢复</button>
    <button class="btn danger" data-action="closeAlarm" data-id="${item.id}">关闭</button>
  `);
}

async function loadLogs() {
  const query = new URLSearchParams();
  const operationType = $("#logOperationType").value.trim();
  const target = $("#logTarget").value.trim();
  const result = $("#logResult").value.trim();
  const operatorName = $("#logOperatorName").value.trim();
  if (operationType) query.set("operationType", operationType);
  if (target) query.set("target", target);
  if (result) query.set("result", result);
  if (operatorName) query.set("operatorName", operatorName);
  const data = await api(`/api/operation-logs?${query}`).catch(() => []);
  $("#logsTable").innerHTML = table(data, [
    ["id", "ID"],
    ["operationType", "操作类型"],
    ["target", "目标"],
    ["targetId", "目标ID"],
    ["operatorName", "操作人"],
    ["result", "结果"],
    ["detail", "详情"],
    ["errorMessage", "错误"],
    ["createdAt", "时间"]
  ]);
}

async function alarmAction(id, action) {
  await api(`/api/alarms/${id}/${action}`, { method: "POST" });
  toast("告警状态已更新");
  await loadAlarms();
}

async function askAi() {
  const message = $("#aiMessage").value.trim();
  if (!message) throw new Error("请输入问题");
  const plotId = numberOrNull($("#aiPlot").value);
  const deviceCode = $("#aiDevice").value.trim();
  const data = await api("/api/ai/chat", {
    method: "POST",
    body: {
      conversationId: state.user?.userId ? `web-user-${state.user.userId}` : "web-console",
      plotId,
      message,
      context: {
        deviceCode
      },
      forceCommit: false
    }
  });

  $("#aiAnswer").textContent = JSON.stringify(data, null, 2);
  if (data.actionProposal) {
    await handleAiAction(data.actionProposal);
  }
}

async function handleAiAction(action) {
  if (action.type === "NAVIGATE" && action.route) {
    toast(`AI 建议跳转：${action.title || action.route}`);
    const page = routeToPage(action.route);
    if (page && confirm(`是否跳转到 ${action.title || page}？`)) {
      state.page = page;
      localStorage.setItem("sa_page", page);
      renderNav();
      await renderPage();
    }
    return;
  }

  if ((action.type === "CONTROL_DEVICE" || action.type === "CONTROL_LIGHT") && action.requiresConfirmation) {
    if (!confirm(buildActionConfirmText(action))) {
      return;
    }
  }

  if (action.type === "CONTROL_DEVICE") {
    const payload = action.payload || {};
    if (!payload.deviceCode) {
      throw new Error("AI 控制动作缺少设备编号");
    }
    const query = new URLSearchParams(payload);
    const data = await api(`/api/control/send?${query}`, { method: "POST" });
    toast(`AI 控制命令已执行：${data.commandNo || data.id}`);
    await watchCommandStatus(data.commandNo);
    return;
  }

  if (action.type === "CONTROL_LIGHT") {
    const payload = action.payload || {};
    if (!payload.deviceCode) {
      throw new Error("AI 补光动作缺少设备编号");
    }
    const data = await api("/api/light/control", {
      method: "POST",
      body: payload
    });
    toast(`AI 补光命令已执行：${data.commandNo || data.id}`);
    await watchCommandStatus(data.commandNo);
  }
}

function buildActionConfirmText(action) {
  const payload = action.payload || {};
  let commandText = action.type;

  if (payload.commandType === "PUMP_ON") {
    commandText = "开启水泵";
  } else if (payload.commandType === "PUMP_OFF") {
    commandText = "关闭水泵";
  } else if (payload.action === "ON") {
    commandText = "开启补光灯";
  } else if (payload.action === "OFF") {
    commandText = "关闭补光灯";
  }

  return [
    "AI 建议执行以下硬件操作：",
    "",
    `操作：${commandText}`,
    `设备：${payload.deviceCode || "未指定"}`,
    `原因：${action.description || "用户通过对话发出控制指令"}`,
    "",
    "确认执行吗？"
  ].join("\n");
}

async function watchCommandStatus(commandNo) {
  if (!commandNo) return;

  for (let i = 0; i < 10; i++) {
    const command = await api(`/api/control/commands/${encodeURIComponent(commandNo)}`);
    if (command.status === "SUCCESS") {
      toast("设备已确认执行成功");
      return;
    }
    if (command.status === "FAILED" || command.status === "TIMEOUT") {
      throw new Error(command.errorMessage || "设备控制失败");
    }
    await delay(1000);
  }

  toast("命令已发送，正在等待设备确认");
}

async function detectPest() {
  const file = $("#pestFile").files[0];
  if (!file) throw new Error("请选择图片");
  const plotId = $("#pestPlot").value;
  const form = new FormData();
  form.append("file", file);
  const url = plotId ? `/api/ai/pest/detect?plotId=${encodeURIComponent(plotId)}` : "/api/ai/pest/detect";
  const data = await api(url, { method: "POST", form });
  $("#pestResult").textContent = JSON.stringify(data, null, 2);
  await loadPestRecords();
}

async function loadPestRecords() {
  const plotId = $("#pestPlot")?.value || "";
  const query = plotId ? `?plotId=${encodeURIComponent(plotId)}` : "";
  const data = await api(`/api/ai/pest/records${query}`).catch(() => []);
  $("#pestRecords").innerHTML = table(data, [
    ["id", "ID"],
    ["plotId", "地块"],
    ["pestId", "害虫ID"],
    ["pestName", "害虫名称"],
    ["confidence", "置信度"],
    ["source", "来源"],
    ["createdAt", "时间"]
  ]);
}

async function api(path, options = {}) {
  const headers = {};
  if (state.token) {
    headers.Authorization = `Bearer ${state.token}`;
  }
  let body;
  if (options.form) {
    body = options.form;
  } else if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(options.body);
  }

  const response = await fetch(`${state.apiBase}${path}`, {
    method: options.method || "GET",
    headers,
    body
  });

  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch (error) {
    json = text;
  }

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${typeof json === "string" ? json : JSON.stringify(json)}`);
  }

  if (options.rawResult) return json;
  if (json && typeof json === "object" && "code" in json) {
    if (json.code !== 200) {
      throw new Error(json.message || `业务错误：${json.code}`);
    }
    return json.data;
  }
  return json;
}

function fillPlotSelect(select, includeBlank = false) {
  if (!select) return;
  const options = includeBlank ? [`<option value="">全部/未选择</option>`] : [];
  options.push(...state.plots.map((plot) => `<option value="${plot.id}">${escapeHtml(plot.name || `地块${plot.id}`)} (${plot.id})</option>`));
  select.innerHTML = options.join("");
}

function fillDeviceSelect(select) {
  if (!select) return;
  select.innerHTML = state.devices.map((device) => `
    <option value="${escapeHtml(device.deviceCode || "")}">
      ${escapeHtml(device.deviceName || device.deviceCode || `设备${device.id}`)}
    </option>
  `).join("");
}

function fillAiDeviceSelect() {
  const select = $("#aiDevice");
  if (!select) return;

  const plotId = $("#aiPlot")?.value || "";
  const devices = plotId
    ? state.devices.filter((device) => String(device.plotId) === String(plotId))
    : state.devices;

  const options = [`<option value="">选择控制设备</option>`];
  options.push(...devices.map((device) => `
    <option value="${escapeHtml(device.deviceCode || "")}">
      ${escapeHtml(device.deviceName || device.deviceCode || `设备${device.id}`)}
    </option>
  `));
  select.innerHTML = options.join("");
}

function firstPlotId() {
  return state.plots[0]?.id || "";
}

function deviceByPlot(plotId) {
  if (!plotId) return null;
  return state.devices.find((device) => String(device.plotId) === String(plotId));
}

function telemetryMetrics(data) {
  if (!data) return empty("暂无遥测数据");
  return [
    metric("土壤湿度", data.soilMoisture, "%"),
    metric("空气温度", data.airTemperature, "℃"),
    metric("空气湿度", data.airHumidity, "%"),
    metric("照度", data.illuminance, "lux"),
    metric("水泵", data.pumpStatus || "-", ""),
    metric("补光", data.lightStatus || "-", ""),
    metric("设备", data.deviceCode || data.deviceId || "-", ""),
    metric("采集时间", data.collectedAt || "-", "")
  ].join("");
}

function stat(label, value) {
  return `<div class="stat"><span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong></div>`;
}

function metric(label, value, unit) {
  return `<div class="metric"><span>${escapeHtml(label)}</span><strong>${escapeHtml(value ?? "-")}</strong> ${escapeHtml(unit)}</div>`;
}

function table(data, columns, actions) {
  const rows = Array.isArray(data) ? data : [];
  if (!rows.length) return empty("暂无数据");
  return `
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            ${columns.map(([, title]) => `<th>${escapeHtml(title)}</th>`).join("")}
            ${actions ? "<th>操作</th>" : ""}
          </tr>
        </thead>
        <tbody>
          ${rows.map((item) => `
            <tr>
              ${columns.map(([key]) => `<td>${formatCell(item[key], key)}</td>`).join("")}
              ${actions ? `<td><div class="mini-actions">${actions(item)}</div></td>` : ""}
            </tr>
          `).join("")}
        </tbody>
      </table>
    </div>
  `;
}

function formatCell(value, key) {
  if (value === null || value === undefined || value === "") return "-";
  if (key === "status") {
    const text = String(value);
    const cls = text.includes("ONLINE") || text.includes("SUCCESS") || text.includes("ACTIVE") ? "ok"
      : text.includes("OFFLINE") || text.includes("FAILED") || text.includes("DISABLED") ? "bad"
      : "warn";
    return `<span class="tag ${cls}">${escapeHtml(text)}</span>`;
  }
  if (typeof value === "object") return escapeHtml(JSON.stringify(value));
  return escapeHtml(String(value));
}

function empty(text) {
  return `<div class="metric"><span>${escapeHtml(text)}</span><strong>-</strong></div>`;
}

function toast(message, error = false) {
  const notice = $("#notice");
  notice.textContent = message;
  notice.classList.toggle("error", error);
}

function numberOrNull(value) {
  if (value === null || value === undefined || String(value).trim() === "") return null;
  return Number(value);
}

function intOrNull(value) {
  const number = numberOrNull(value);
  return number === null ? null : parseInt(number, 10);
}

function random(min, max) {
  return Number((Math.random() * (max - min) + min).toFixed(2));
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function trimTime(value) {
  if (!value) return "";
  return String(value).slice(0, 5);
}

function routeToPage(route) {
  const map = {
    "/": "dashboard",
    "/plots": "plots",
    "/devices": "devices",
    "/telemetry": "telemetry",
    "/alarms": "alarms",
    "/irrigation": "control",
    "/light": "control",
    "/pest": "pest",
    "/ai": "ai"
  };
  return map[route];
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

init();

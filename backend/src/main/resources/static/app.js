const adminKey = "applocker.adminToken";
const signIn = document.querySelector("#sign-in");
const shell = document.querySelector("#shell");
const enrollment = document.querySelector("#enrollment");
const policies = document.querySelector("#policies");
const signInForm = document.querySelector("#sign-in-form");
const signInError = document.querySelector("#sign-in-error");
const listError = document.querySelector("#list-error");
const rows = document.querySelector("#token-rows");
const count = document.querySelector("#count");
const search = document.querySelector("#search");
const backdrop = document.querySelector("#backdrop");
const form = document.querySelector("#token-form");
const ready = document.querySelector("#ready");
const formError = document.querySelector("#form-error");
const deviceLimit = document.querySelector("#device-limit");
const noLimit = document.querySelector("#no-limit");
const customDays = document.querySelector("#custom-days");
const customDayCount = document.querySelector("#custom-day-count");

let tokens = [];
let selectedDays = 1;
let setupToken = null;

function adminToken() {
  return localStorage.getItem(adminKey) || "";
}

function show(element, visible) {
  element.hidden = !visible;
}

async function api(path, options = {}) {
  const headers = new Headers(options.headers || {});
  headers.set("Authorization", "Bearer " + adminToken());
  if (options.body) {
    headers.set("Content-Type", "application/json");
  }
  const response = await fetch(path, { ...options, headers });
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const error = new Error(body && body.error ? body.error : "Request failed");
    error.status = response.status;
    throw error;
  }
  return body;
}

function daysLeft(value) {
  const days = Math.ceil((new Date(value) - Date.now()) / 86400000);
  if (days <= 1) {
    return "1 day";
  }
  return days + " days";
}

function statusOf(item) {
  if (item.revoked) {
    return { text: "Revoked", className: "pill revoked" };
  }
  if (item.deviceLimit != null && item.enrolledCount >= item.deviceLimit) {
    return { text: "Limit reached", className: "pill limit" };
  }
  return { text: "Active", className: "pill" };
}

function render() {
  const query = search.value.trim().toLowerCase();
  const visible = tokens.filter((item) => item.label.toLowerCase().includes(query));
  rows.replaceChildren();
  for (const item of visible) {
    const tr = document.createElement("tr");
    tr.append(cell(item.label), enrolledCell(item), cell(daysLeft(item.expiresAt)), statusCell(item), actions(item));
    rows.append(tr);
  }
  const noun = visible.length === 1 ? "token" : "tokens";
  count.textContent = visible.length + " " + noun;
}

function cell(text) {
  const td = document.createElement("td");
  td.textContent = text;
  return td;
}

function enrolledCell(item) {
  const td = document.createElement("td");
  const wrap = document.createElement("div");
  wrap.className = "enrolled";
  const meter = document.createElement("div");
  const fill = document.createElement("span");
  const full = item.deviceLimit != null && item.enrolledCount >= item.deviceLimit;
  meter.className = full ? "meter full" : "meter";
  const ratio = item.deviceLimit == null ? 0.08 : Math.min(1, item.enrolledCount / item.deviceLimit);
  fill.style.width = Math.max(ratio * 100, item.enrolledCount > 0 ? 4 : 0) + "%";
  meter.append(fill);
  const label = document.createElement("span");
  label.textContent = item.deviceLimit == null
    ? item.enrolledCount + " enrolled"
    : item.enrolledCount + " of " + item.deviceLimit;
  wrap.append(meter, label);
  td.append(wrap);
  return td;
}

function statusCell(item) {
  const td = document.createElement("td");
  const status = statusOf(item);
  const pill = document.createElement("span");
  pill.className = status.className;
  pill.textContent = status.text;
  td.append(pill);
  return td;
}

function actions(item) {
  const td = document.createElement("td");
  td.className = "row-actions";
  const view = document.createElement("button");
  view.type = "button";
  view.className = "view";
  view.textContent = "View setup";
  view.addEventListener("click", () => openReady(item));
  const menuButton = document.createElement("button");
  menuButton.type = "button";
  menuButton.className = "menu-button";
  menuButton.textContent = "⋮";
  menuButton.setAttribute("aria-label", "Token actions");
  const menu = document.createElement("div");
  menu.className = "menu";
  menu.hidden = true;
  const revoke = document.createElement("button");
  revoke.type = "button";
  revoke.textContent = "Revoke";
  revoke.addEventListener("click", () => revokeToken(item.label));
  menu.append(revoke);
  menuButton.addEventListener("click", (event) => {
    event.stopPropagation();
    document.querySelectorAll(".menu").forEach((other) => { other.hidden = other === menu ? menu.hidden : true; });
    menu.hidden = !menu.hidden;
  });
  td.append(view, menuButton, menu);
  return td;
}

async function copy(value) {
  await navigator.clipboard.writeText(value);
}

async function loadTokens() {
  show(listError, false);
  try {
    tokens = await api("/api/v1/enrollment-tokens");
    render();
    show(signIn, false);
    show(shell, true);
  } catch (error) {
    if (error.status === 401) {
      localStorage.removeItem(adminKey);
      show(shell, false);
      show(signIn, true);
      signInError.textContent = "That admin token was rejected.";
      show(signInError, true);
      return;
    }
    listError.textContent = error.message;
    show(listError, true);
  }
}

async function revokeToken(label) {
  if (!confirm("Revoke " + label + "? Devices already enrolled stay enrolled.")) {
    return;
  }
  await api("/api/v1/enrollment-tokens/revocations", {
    method: "POST",
    body: JSON.stringify({ label })
  });
  await loadTokens();
}

function showSection(name) {
  document.querySelectorAll(".nav[data-section]").forEach((button) => {
    button.classList.toggle("active", button.dataset.section === name);
  });
  show(enrollment, name === "enrollment");
  show(policies, name === "policies");
  if (name === "policies") {
    loadWorkspace();
  }
}

function selectedExpiryDays() {
  return selectedDays === "custom" ? Number(customDayCount.value) : Number(selectedDays);
}

function openGenerate() {
  form.reset();
  deviceLimit.disabled = false;
  deviceLimit.value = "50";
  selectedDays = 1;
  document.querySelectorAll(".preset").forEach((button) => {
    button.classList.toggle("selected", button.dataset.days === "1");
  });
  show(customDays, false);
  show(formError, false);
  show(form, true);
  show(ready, false);
  document.querySelector("#modal-title").textContent = "Generate PPKG token";
  show(backdrop, true);
}

function openReady(item) {
  setupToken = {
    label: item.label,
    deviceLimit: item.deviceLimit,
    expiresAt: item.expiresAt,
    discoveryUrl: item.discoveryUrl,
    secret: item.secret || item.tokenId,
    authPolicy: item.authPolicy || "OnPremise"
  };
  const limitText = setupToken.deviceLimit == null ? "no device limit" : "up to " + setupToken.deviceLimit + " devices";
  document.querySelector("#banner").textContent = setupToken.label + " · " + limitText + " · expires in " + daysLeft(setupToken.expiresAt);
  document.querySelector("#package-hint").textContent = "Create a desktop provisioning package named " + setupToken.label + ". Then go to Runtime settings, Workplace, Enrollments, and add a new entry with:";
  const values = document.querySelector("#values");
  values.replaceChildren();
  for (const [name, value] of [
    ["AuthPolicy", setupToken.authPolicy],
    ["UPN", "device@lab.test"],
    ["Server URL", setupToken.discoveryUrl],
    ["Secret", setupToken.secret]
  ]) {
    const row = document.createElement("div");
    row.className = "value-row";
    const label = document.createElement("span");
    const code = document.createElement("code");
    const button = document.createElement("button");
    label.textContent = name;
    code.textContent = value;
    button.type = "button";
    button.className = "copy";
    button.textContent = "Copy";
    button.addEventListener("click", () => copy(value));
    row.append(label, code, button);
    values.append(row);
  }
  show(document.querySelector("#why-body"), false);
  show(form, false);
  show(ready, true);
  document.querySelector("#modal-title").textContent = "Your token is ready";
  show(backdrop, true);
}

signInForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  localStorage.setItem(adminKey, document.querySelector("#admin-token").value.trim());
  await loadTokens();
});

document.querySelector("#sign-out").addEventListener("click", () => {
  localStorage.removeItem(adminKey);
  show(shell, false);
  show(signIn, true);
});

document.querySelectorAll(".nav[data-section]").forEach((button) => {
  button.addEventListener("click", () => showSection(button.dataset.section));
});

search.addEventListener("input", render);
document.querySelector("#open-generate").addEventListener("click", openGenerate);
document.querySelector("#close-modal").addEventListener("click", () => show(backdrop, false));
document.querySelector("#cancel-generate").addEventListener("click", () => show(backdrop, false));
document.querySelector("#done").addEventListener("click", async () => {
  show(backdrop, false);
  await loadTokens();
});

noLimit.addEventListener("change", () => {
  deviceLimit.disabled = noLimit.checked;
});

document.querySelectorAll(".preset").forEach((button) => {
  button.addEventListener("click", () => {
    selectedDays = button.dataset.days === "custom" ? "custom" : Number(button.dataset.days);
    document.querySelectorAll(".preset").forEach((other) => other.classList.toggle("selected", other === button));
    show(customDays, selectedDays === "custom");
  });
});

document.querySelector("#why-toggle").addEventListener("click", () => {
  const body = document.querySelector("#why-body");
  show(body, body.hidden);
});

document.querySelector("#download-steps").addEventListener("click", () => {
  const text = [
    "Package name: " + setupToken.label,
    "AuthPolicy: " + setupToken.authPolicy,
    "UPN: device@lab.test",
    "Server URL: " + setupToken.discoveryUrl,
    "Secret: " + setupToken.secret,
    "Leave the enrollment URL and policy URL empty."
  ].join("\n");
  const link = document.createElement("a");
  link.href = URL.createObjectURL(new Blob([text], { type: "text/plain" }));
  link.download = setupToken.label.replaceAll(/[^\w.-]+/g, "-") + "-enrollment.txt";
  link.click();
});

document.addEventListener("click", () => {
  document.querySelectorAll(".menu").forEach((menu) => { menu.hidden = true; });
});

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  show(formError, false);
  const payload = {
    label: document.querySelector("#label").value.trim(),
    deviceLimit: noLimit.checked ? null : Number(deviceLimit.value),
    ttlDays: selectedExpiryDays()
  };
  try {
    const created = await api("/api/v1/enrollment-tokens", {
      method: "POST",
      body: JSON.stringify(payload)
    });
    await loadTokens();
    openReady(created);
  } catch (error) {
    formError.textContent = error.message;
    show(formError, true);
  }
});

if (adminToken()) {
  loadTokens();
}
setInterval(() => {
  if (adminToken() && !shell.hidden && !enrollment.hidden) {
    loadTokens();
  }
}, 15000);

const policyError = document.querySelector("#policy-error");
const policyBackdrop = document.querySelector("#policy-backdrop");
const policyForm = document.querySelector("#policy-form");
const policyFormError = document.querySelector("#policy-form-error");
const collectionNav = document.querySelector("#collection-nav");
const ruleHead = document.querySelector("#rule-head");
const ruleRows = document.querySelector("#rule-rows");
const appSearch = document.querySelector("#app-search");
const appFilters = document.querySelector("#app-filters");

let workspace = null;
let selectedCollection = "Appx";
let storeApps = [];
let collectionRules = [];
let appFilter = "all";
let busy = false;

function currentCollection() {
  return workspace.collections.find((item) => item.id === selectedCollection);
}

function showPolicyError(error) {
  policyError.textContent = error && error.message ? error.message : "Request failed";
  show(policyError, true);
}

async function loadWorkspace() {
  show(policyError, false);
  try {
    workspace = await api("/api/v1/applocker/workspace");
    if (!workspace.collections.some((item) => item.id === selectedCollection)) {
      selectedCollection = "Appx";
    }
    renderNav();
    await loadCollection();
  } catch (error) {
    showPolicyError(error);
  }
}

async function loadCollection() {
  if (selectedCollection === "Appx") {
    storeApps = await api("/api/v1/applocker/store-apps");
  } else {
    collectionRules = await api("/api/v1/applocker/rules?collection=" + selectedCollection);
  }
  renderManager();
}

function renderNav() {
  collectionNav.replaceChildren();
  for (const item of workspace.collections) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = item.id === selectedCollection ? "collection active" : "collection";
    const label = document.createElement("span");
    label.textContent = item.label;
    const count = document.createElement("span");
    count.textContent = String(item.ruleCount);
    button.append(label, count);
    button.addEventListener("click", () => {
      selectedCollection = item.id;
      appFilter = "all";
      appSearch.value = "";
      loadWorkspace();
    });
    collectionNav.append(button);
  }
  const identity = document.querySelector("#identity");
  identity.classList.toggle("running", workspace.identityRunning);
  document.querySelector("#identity-text").textContent = workspace.identityRunning
    ? "Service (AppIDSvc) is running. AppLocker can enforce rules."
    : "Service (AppIDSvc) is not running. AppLocker needs it before rules are enforced.";
}

function renderManager() {
  const collection = currentCollection();
  document.querySelector("#collection-crumb").textContent = collection.label;
  document.querySelector("#collection-title").textContent = collection.title;
  document.querySelector("#collection-help").textContent = collection.description;
  const banner = document.querySelector("#audit-banner");
  if (collection.enforcementMode === "AuditOnly") {
    banner.hidden = false;
    banner.textContent = collection.sentToDevices
      ? "Audit only. Nothing is blocked yet. Install and launch are allowed and logged, so you can see what a rule would do before you enforce it."
      : "Audit only is saved for this collection. Enrolled devices receive the packaged app collection.";
  } else if (collection.sentToDevices) {
    banner.hidden = false;
    banner.textContent = "Enforce is on. A block rule stops installation and launch of that packaged app. Allow rules let it install and launch. This does not block the Microsoft Store download page.";
  } else {
    banner.hidden = false;
    banner.textContent = "Enforce is saved for this collection. It is not sent to enrolled devices. Packaged app rules are the collection those devices receive.";
  }
  document.querySelectorAll("#enforcement-switch button").forEach((button) => {
    button.classList.toggle("selected", button.dataset.mode === collection.enforcementMode);
  });
  document.querySelector("#enforcement-help").textContent = collection.sentToDevices
    ? "Audit records the decision. Enforce applies it on the next management check-in."
    : "This choice is stored with the collection.";
  const toggle = document.querySelector("#default-allow");
  toggle.checked = collection.defaultAllow;
  document.querySelector("#default-title").textContent = collection.id === "Appx"
    ? "Allow Everyone to run all signed packaged apps"
    : "Allow the standard Windows folders";
  document.querySelector("#default-help").textContent = collection.id === "Appx"
    ? "Apps with no rule of their own follow this rule. Turn it off to allow only the packaged apps you list. Leave it on before you enforce."
    : "Turn this on to allow Program Files, Windows, and every file for Administrators.";
  renderCoverage(collection);
  renderFilters(collection);
  renderTable(collection);
}

function counts(collection) {
  if (collection.id === "Appx") {
    return {
      allowed: storeApps.filter((item) => item.action === "Allow").length,
      blocked: storeApps.filter((item) => item.action === "Deny").length,
      none: storeApps.filter((item) => item.action === "None").length,
      total: storeApps.length
    };
  }
  const custom = collectionRules.filter((item) => !item.defaultRule);
  return {
    allowed: custom.filter((item) => item.action === "Allow").length,
    blocked: custom.filter((item) => item.action === "Deny").length,
    none: 0,
    total: custom.length
  };
}

function renderCoverage(collection) {
  const tally = counts(collection);
  const coverage = document.querySelector("#coverage");
  coverage.replaceChildren();
  const items = collection.id === "Appx"
    ? [["Allowed", tally.allowed, "allow"], ["Blocked", tally.blocked, "block"], ["No rule", tally.none, "none"]]
    : [["Allowed", tally.allowed, "allow"], ["Blocked", tally.blocked, "block"]];
  for (const [label, value, kind] of items) {
    const block = document.createElement("div");
    const number = document.createElement("strong");
    number.className = kind;
    number.textContent = String(value);
    const text = document.createElement("span");
    text.textContent = label;
    block.append(number, text);
    coverage.append(block);
  }
  document.querySelector("#coverage-help").textContent = collection.id === "Appx"
    ? "A deny rule wins over an allow rule."
    : "Counts are the rules saved in this collection.";
}

function renderFilters(collection) {
  const tally = counts(collection);
  const filters = collection.id === "Appx"
    ? [["all", "All " + tally.total], ["allowed", "Allowed " + tally.allowed], ["blocked", "Blocked " + tally.blocked], ["none", "No rule " + tally.none]]
    : [["all", "All " + tally.total], ["allowed", "Allowed " + tally.allowed], ["blocked", "Blocked " + tally.blocked]];
  appFilters.replaceChildren();
  for (const [id, label] of filters) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = appFilter === id ? "selected" : "";
    button.textContent = label;
    button.addEventListener("click", () => {
      appFilter = id;
      renderManager();
    });
    appFilters.append(button);
  }
}

function visibleStoreApps() {
  const query = appSearch.value.trim().toLowerCase();
  return storeApps.filter((item) => {
    const text = (item.packageName + " " + item.publisher + " " + item.version).toLowerCase();
    if (query && !text.includes(query)) {
      return false;
    }
    if (appFilter === "allowed") {
      return item.action === "Allow";
    }
    if (appFilter === "blocked") {
      return item.action === "Deny";
    }
    if (appFilter === "none") {
      return item.action === "None";
    }
    return true;
  });
}

function visibleRules() {
  const query = appSearch.value.trim().toLowerCase();
  return collectionRules.filter((item) => {
    if (item.defaultRule) {
      return false;
    }
    const text = [item.name, item.path, item.publisherName, item.productName, item.binaryName].join(" ").toLowerCase();
    if (query && !text.includes(query)) {
      return false;
    }
    if (appFilter === "allowed") {
      return item.action === "Allow";
    }
    if (appFilter === "blocked") {
      return item.action === "Deny";
    }
    return true;
  });
}

function renderTable(collection) {
  ruleHead.replaceChildren();
  ruleRows.replaceChildren();
  const head = document.createElement("tr");
  const titles = collection.id === "Appx"
    ? ["App", "Publisher", "Rule for Everyone", "Result"]
    : ["Rule", "Condition", "Value", "Rule for Everyone", "Result", ""];
  for (const title of titles) {
    const th = document.createElement("th");
    th.textContent = title;
    head.append(th);
  }
  ruleHead.append(head);
  const rows = collection.id === "Appx" ? visibleStoreApps() : visibleRules();
  if (rows.length === 0) {
    const tr = document.createElement("tr");
    const td = document.createElement("td");
    td.colSpan = titles.length;
    td.textContent = collection.id === "Appx"
      ? "No packaged apps match this filter."
      : "No rules in this collection yet.";
    tr.append(td);
    ruleRows.append(tr);
    return;
  }
  for (const item of rows) {
    ruleRows.append(collection.id === "Appx" ? storeRow(item) : ruleRow(item));
  }
}

function storeRow(item) {
  const tr = document.createElement("tr");
  const app = document.createElement("td");
  const name = document.createElement("strong");
  name.textContent = item.packageName;
  const version = document.createElement("p");
  version.className = "sub";
  version.textContent = item.version || "Version from the rule";
  app.append(name, version);
  tr.append(app, cell(publisherLabel(item.publisher)), actionCell(item.action, (action) => setStoreAction(item, action)), resultCell(item.result));
  return tr;
}

function ruleRow(item) {
  const tr = document.createElement("tr");
  tr.append(cell(item.name), cell(item.type), cell(ruleValue(item)), actionCell(item.action, (action) => setRuleAction(item, action)), resultCell(item.result));
  const actions = document.createElement("td");
  const remove = document.createElement("button");
  remove.type = "button";
  remove.className = "ghost";
  remove.textContent = "Remove";
  remove.addEventListener("click", () => removeRule(item.id));
  actions.append(remove);
  tr.append(actions);
  return tr;
}

function publisherLabel(value) {
  if (!value || value === "*") {
    return "Any publisher";
  }
  const match = value.match(/^CN=([^,]+)/i);
  return match ? match[1] : value;
}

function ruleValue(item) {
  if (item.type === "Path") {
    return item.path || "";
  }
  if (item.type === "Hash") {
    return item.name;
  }
  return [item.publisherName, item.productName, item.binaryName].filter(Boolean).join(" / ");
}

function actionCell(action, onChange) {
  const td = document.createElement("td");
  const group = document.createElement("div");
  group.className = "segment";
  for (const [value, label, kind] of [["Allow", "Allow", "allow"], ["Deny", "Block", "block"], ["None", "None", "none"]]) {
    const button = document.createElement("button");
    button.type = "button";
    button.textContent = label;
    button.className = action === value ? "selected " + kind : kind;
    button.addEventListener("click", () => onChange(value));
    group.append(button);
  }
  td.append(group);
  return td;
}

function resultCell(text) {
  const td = document.createElement("td");
  const pill = document.createElement("span");
  const blocked = text.startsWith("Blocked") || text.startsWith("Would be blocked") || text.startsWith("Saved deny");
  const allowed = text.startsWith("Allowed") || text.startsWith("Runs") || text.startsWith("Saved allow");
  pill.className = blocked ? "pill block" : allowed ? "pill allow" : "pill";
  pill.textContent = text;
  td.append(pill);
  return td;
}

async function setStoreAction(item, action) {
  if (busy || item.action === action) {
    return;
  }
  busy = true;
  show(policyError, false);
  try {
    await api("/api/v1/applocker/store-apps", {
      method: "PUT",
      body: JSON.stringify({ packageName: item.packageName, publisher: item.publisher, version: item.version, action })
    });
    await loadWorkspace();
  } catch (error) {
    showPolicyError(error);
  } finally {
    busy = false;
  }
}

async function setRuleAction(item, action) {
  if (busy || action === "None") {
    if (action === "None") {
      await removeRule(item.id);
    }
    return;
  }
  if (item.action === action) {
    return;
  }
  busy = true;
  show(policyError, false);
  try {
    await api("/api/v1/applocker/rules/" + item.id, {
      method: "PUT",
      body: JSON.stringify({ action })
    });
    await loadWorkspace();
  } catch (error) {
    showPolicyError(error);
  } finally {
    busy = false;
  }
}

async function removeRule(id) {
  if (busy) {
    return;
  }
  busy = true;
  show(policyError, false);
  try {
    const headers = new Headers();
    headers.set("Authorization", "Bearer " + adminToken());
    const response = await fetch("/api/v1/applocker/rules/" + id, { method: "DELETE", headers });
    if (!response.ok && response.status !== 204) {
      const body = await response.json();
      throw new Error(body.error || "Request failed");
    }
    await loadWorkspace();
  } catch (error) {
    showPolicyError(error);
  } finally {
    busy = false;
  }
}

document.querySelector("#enforcement-switch").addEventListener("click", async (event) => {
  const button = event.target.closest("button[data-mode]");
  if (!button || busy || !workspace) {
    return;
  }
  const collection = currentCollection();
  if (button.dataset.mode === collection.enforcementMode) {
    return;
  }
  busy = true;
  show(policyError, false);
  try {
    await api("/api/v1/applocker/collections/" + collection.id, {
      method: "PUT",
      body: JSON.stringify({ enforcementMode: button.dataset.mode, defaultAllow: collection.defaultAllow })
    });
    await loadWorkspace();
  } catch (error) {
    showPolicyError(error);
  } finally {
    busy = false;
  }
});

document.querySelector("#default-allow").addEventListener("change", async (event) => {
  if (!workspace) {
    return;
  }
  const collection = currentCollection();
  busy = true;
  show(policyError, false);
  try {
    await api("/api/v1/applocker/collections/" + collection.id, {
      method: "PUT",
      body: JSON.stringify({ enforcementMode: collection.enforcementMode, defaultAllow: event.target.checked })
    });
    await loadWorkspace();
  } catch (error) {
    event.target.checked = collection.defaultAllow;
    showPolicyError(error);
  } finally {
    busy = false;
  }
});

appSearch.addEventListener("input", () => renderManager());

function openRuleForm() {
  if (!workspace) {
    return;
  }
  const collection = currentCollection();
  policyForm.reset();
  document.querySelector("#rule-binary").value = "*";
  show(policyFormError, false);
  document.querySelector("#policy-modal-title").textContent = "Create " + collection.label.toLowerCase() + " rule";
  document.querySelector("#rule-form-help").textContent = collection.description;
  document.querySelector("#policy-mode-label").textContent = collection.sentToDevices ? "Sent to enrolled devices" : "Saved on this server";
  document.querySelector("#rule-safety").textContent = collection.id === "Appx"
    ? "A publisher rule controls installation and launch of that package. It does not block the Microsoft Store download page. A deny-all rule is rejected."
    : "Path, publisher, and hash rules are saved for this collection. A deny-all rule for Everyone is rejected.";
  const type = document.querySelector("#rule-type");
  type.replaceChildren();
  for (const condition of collection.conditions) {
    const option = document.createElement("option");
    option.value = condition;
    option.textContent = condition;
    type.append(option);
  }
  document.querySelector("#product-label").textContent = collection.id === "Appx" ? "Package name" : "Product name";
  document.querySelector("#rule-product").placeholder = collection.id === "Appx" ? "Package name from the installed app" : "Product name, or *";
  show(document.querySelector("#binary-field"), collection.id !== "Appx");
  syncRuleFields();
  show(policyBackdrop, true);
}

function syncRuleFields() {
  const type = document.querySelector("#rule-type").value;
  show(document.querySelector("#path-fields"), type === "Path");
  show(document.querySelector("#publisher-fields"), type === "Publisher");
  show(document.querySelector("#hash-fields"), type === "Hash");
}

document.querySelector("#open-rule").addEventListener("click", openRuleForm);
document.querySelector("#close-policy").addEventListener("click", () => show(policyBackdrop, false));
document.querySelector("#cancel-policy").addEventListener("click", () => show(policyBackdrop, false));
document.querySelector("#rule-type").addEventListener("change", syncRuleFields);

policyForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  show(policyFormError, false);
  const collection = currentCollection();
  const type = document.querySelector("#rule-type").value;
  const payload = {
    collection: collection.id,
    type,
    action: document.querySelector("#rule-action").value,
    name: document.querySelector("#rule-name").value.trim(),
    userOrGroupSid: document.querySelector("#rule-sid").value,
    path: document.querySelector("#rule-path").value.trim(),
    publisherName: document.querySelector("#rule-publisher").value.trim(),
    productName: document.querySelector("#rule-product").value.trim(),
    binaryName: collection.id === "Appx" ? "*" : document.querySelector("#rule-binary").value.trim(),
    binaryVersionLow: document.querySelector("#rule-version-low").value.trim(),
    binaryVersionHigh: document.querySelector("#rule-version-high").value.trim(),
    hashData: document.querySelector("#rule-hash").value.trim(),
    sourceFileName: document.querySelector("#rule-file").value.trim(),
    sourceFileLength: Number(document.querySelector("#rule-length").value)
  };
  if (type !== "Hash") {
    delete payload.hashData;
    delete payload.sourceFileName;
    delete payload.sourceFileLength;
  }
  try {
    await api("/api/v1/applocker/rules", { method: "POST", body: JSON.stringify(payload) });
    show(policyBackdrop, false);
    await loadWorkspace();
  } catch (error) {
    policyFormError.textContent = error.message;
    show(policyFormError, true);
  }
});

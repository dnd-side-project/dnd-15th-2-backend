const fs = require("node:fs");

function requiredEnv(name) {
  const value = process.env[name]?.trim();
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

function repository() {
  const value = requiredEnv("GITHUB_REPOSITORY");
  const [owner, repo] = value.split("/");
  if (!owner || !repo) {
    throw new Error(`Invalid GITHUB_REPOSITORY: ${value}`);
  }
  return { owner, repo };
}

function eventPayload() {
  return JSON.parse(
    fs.readFileSync(requiredEnv("GITHUB_EVENT_PATH"), "utf8")
  );
}

async function request(url, options = {}) {
  const response = await fetch(url, options);
  const text = await response.text();
  let data = null;

  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }

  if (!response.ok) {
    const detail =
      typeof data === "string" ? data : JSON.stringify(data, null, 2);
    throw new Error(`${options.method || "GET"} ${url} → ${response.status}\n${detail}`);
  }

  return data;
}

async function github(path, options = {}) {
  const token = requiredEnv("GITHUB_TOKEN");
  const apiUrl = process.env.GITHUB_API_URL || "https://api.github.com";

  return request(`${apiUrl}${path}`, {
    ...options,
    headers: {
      Accept: "application/vnd.github+json",
      Authorization: `Bearer ${token}`,
      "X-GitHub-Api-Version": "2022-11-28",
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...options.headers,
    },
  });
}

async function jira(path, options = {}) {
  const baseUrl = requiredEnv("JIRA_BASE_URL").replace(/\/+$/, "");
  const email = requiredEnv("JIRA_EMAIL");
  const token = requiredEnv("JIRA_API_TOKEN");
  const authorization = Buffer.from(`${email}:${token}`).toString("base64");

  return request(`${baseUrl}${path}`, {
    ...options,
    headers: {
      Accept: "application/json",
      Authorization: `Basic ${authorization}`,
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...options.headers,
    },
  });
}

function jsonBody(value) {
  return JSON.stringify(value);
}

function extractFormField(body, label) {
  const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const pattern = new RegExp(
    `### ${escaped}\\s*\\n+([\\s\\S]*?)(?=\\n+### |$)`,
    "i"
  );
  const match = (body || "").match(pattern);
  if (!match) {
    return "";
  }
  const value = match[1].trim();
  return value === "_No response_" ? "" : value;
}

function parseJiraKey(value) {
  const match = (value || "").toUpperCase().match(/\b([A-Z][A-Z0-9]+-\d+)\b/);
  return match?.[1] || "";
}

function cleanTitle(title) {
  return (title || "")
    .replace(/^\[(?:JIRA|[A-Z][A-Z0-9]+-\d+)\]\s*/i, "")
    .trim();
}

function textParagraph(text) {
  const lines = String(text || "").split(/\r?\n/);
  const content = [];

  lines.forEach((line, index) => {
    if (index > 0) {
      content.push({ type: "hardBreak" });
    }
    if (line) {
      content.push({ type: "text", text: line });
    }
  });

  return {
    type: "paragraph",
    content: content.length ? content : [{ type: "text", text: "-" }],
  };
}

function jiraDescription(sections) {
  return {
    type: "doc",
    version: 1,
    content: sections.flatMap(([label, value]) => [
      textParagraph(label),
      textParagraph(value || "-"),
    ]),
  };
}

module.exports = {
  cleanTitle,
  eventPayload,
  extractFormField,
  github,
  jira,
  jiraDescription,
  jsonBody,
  parseJiraKey,
  repository,
  requiredEnv,
};


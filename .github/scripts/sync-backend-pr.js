const {
  eventPayload,
  github,
  jsonBody,
  parseJiraKey,
  repository,
  requiredEnv,
} = require("./jira-common");

function linkedIssueNumber(body) {
  const match = (body || "").match(
    /\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\s+#(\d+)\b/i
  );
  return match ? Number(match[1]) : 0;
}

function jiraBlock(jiraKey, jiraUrl, issueNumber) {
  return [
    "<!-- jira-sync:start -->",
    `Jira: [${jiraKey}](${jiraUrl})`,
    `GitHub Issue: #${issueNumber}`,
    "<!-- jira-sync:end -->",
  ].join("\n");
}

function applyJiraBlock(body, block) {
  const pattern =
    /<!-- jira-sync:start -->[\s\S]*?<!-- jira-sync:end -->/;
  if (pattern.test(body || "")) {
    return body.replace(pattern, block);
  }
  return `${block}\n\n${body || ""}`.trim();
}

async function main() {
  const payload = eventPayload();
  const pullRequest = payload.pull_request;

  if (!pullRequest) {
    throw new Error("This workflow must run from a pull_request event.");
  }

  const { owner, repo } = repository();
  const issueNumber = linkedIssueNumber(pullRequest.body);

  if (!issueNumber) {
    throw new Error(
      "PR 본문에 `Closes #GitHubIssue번호` 형식의 연결 정보가 필요합니다."
    );
  }

  const issue = await github(
    `/repos/${owner}/${repo}/issues/${issueNumber}`
  );
  const jiraKey = parseJiraKey(issue.title);

  if (!jiraKey) {
    throw new Error(
      `GitHub Issue #${issueNumber}가 아직 Jira 작업과 연결되지 않았습니다.`
    );
  }

  const jiraBaseUrl = requiredEnv("JIRA_BASE_URL").replace(/\/+$/, "");
  const jiraUrl = `${jiraBaseUrl}/browse/${jiraKey}`;
  const cleanPrTitle = pullRequest.title
    .replace(/^\[(?:JIRA|[A-Z][A-Z0-9]+-\d+)\]\s*/i, "")
    .trim();
  const nextTitle = `[${jiraKey}] ${cleanPrTitle}`;
  const nextBody = applyJiraBlock(
    pullRequest.body || "",
    jiraBlock(jiraKey, jiraUrl, issueNumber)
  );

  if (
    pullRequest.title !== nextTitle ||
    pullRequest.body !== nextBody
  ) {
    await github(`/repos/${owner}/${repo}/pulls/${pullRequest.number}`, {
      method: "PATCH",
      body: jsonBody({
        title: nextTitle,
        body: nextBody,
      }),
    });
  }

  console.log(
    `Applied ${jiraKey} from GitHub Issue #${issueNumber} to PR #${pullRequest.number}`
  );
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});


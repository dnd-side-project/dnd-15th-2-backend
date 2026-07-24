const {
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
} = require("./jira-common");

async function main() {
  const payload = eventPayload();
  const { owner, repo } = repository();
  const issueNumber = Number(
    process.env.ISSUE_NUMBER || payload.issue?.number
  );

  if (!Number.isInteger(issueNumber) || issueNumber <= 0) {
    throw new Error("A valid GitHub Issue number is required.");
  }

  const issue =
    payload.issue?.number === issueNumber
      ? payload.issue
      : await github(`/repos/${owner}/${repo}/issues/${issueNumber}`);

  const comments = await github(
    `/repos/${owner}/${repo}/issues/${issueNumber}/comments?per_page=100`
  );
  const markerMatch = comments
    .map((comment) =>
      (comment.body || "").match(
        /<!--\s*jira-child:([A-Z][A-Z0-9]+-\d+)\s*-->/
      )
    )
    .find(Boolean);

  let childKey = markerMatch?.[1];
  const title = cleanTitle(issue.title);

  if (!childKey) {
    const parentKey = parseJiraKey(
      extractFormField(issue.body, "Parent Jira ticket")
    );

    if (!parentKey) {
      throw new Error(
        'Issue Form의 "Parent Jira ticket"에 올바른 Jira 키가 필요합니다.'
      );
    }

    const parent = await jira(
      `/rest/api/3/issue/${encodeURIComponent(
        parentKey
      )}?fields=summary,project,status`
    );

    const issueTypeId = process.env.JIRA_CHILD_ISSUE_TYPE_ID?.trim();
    const issueTypeName =
      process.env.JIRA_CHILD_ISSUE_TYPE_NAME?.trim() || "Task";
    const issueType = issueTypeId
      ? { id: issueTypeId }
      : { name: issueTypeName };

    const created = await jira("/rest/api/3/issue", {
      method: "POST",
      body: jsonBody({
        fields: {
          project: { key: parent.fields.project.key },
          parent: { key: parentKey },
          issuetype: issueType,
          summary: title,
          description: jiraDescription([
            ["Work type", extractFormField(issue.body, "Work type")],
            ["Details", extractFormField(issue.body, "Details")],
            [
              "Acceptance criteria",
              extractFormField(issue.body, "Acceptance criteria"),
            ],
            [
              "Backend impact",
              extractFormField(issue.body, "Backend impact"),
            ],
            [
              "Dependencies or blockers",
              extractFormField(issue.body, "Dependencies or blockers"),
            ],
          ]),
        },
      }),
    });

    childKey = created.key;
    const jiraBaseUrl = requiredEnv("JIRA_BASE_URL").replace(/\/+$/, "");

    await github(`/repos/${owner}/${repo}/issues/${issueNumber}/comments`, {
      method: "POST",
      body: jsonBody({
        body: [
          `<!-- jira-child:${childKey} -->`,
          `Jira 작업: [${childKey}](${jiraBaseUrl}/browse/${childKey})`,
          `상위 티켓: [${parentKey}](${jiraBaseUrl}/browse/${parentKey})`,
        ].join("\n\n"),
      }),
    });
  }

  const nextTitle = `[${childKey}] ${title}`;
  if (issue.title !== nextTitle) {
    await github(`/repos/${owner}/${repo}/issues/${issueNumber}`, {
      method: "PATCH",
      body: jsonBody({ title: nextTitle }),
    });
  }

  console.log(`Applied Jira key ${childKey} to GitHub Issue #${issueNumber}`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});


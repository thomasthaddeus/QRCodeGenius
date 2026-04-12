$Repo = "thomasthaddeus/QRCodeGenius" # Replace with your GitHub username and repository
$IssuesFile = "issues.json" # Path to your JSON file with issues

# Read the JSON file and convert it into an object
$Issues = Get-Content $IssuesFile | ConvertFrom-Json

# Iterate over each issue and create it in the repository
foreach ($issue in $Issues) {
    $TITLE = $issue.title
    $BODY = $issue.body

    # Create the issue
    gh issue create --title "$TITLE" --body "$BODY" --repo "$REPO"
    Write-Host "Issue '$TITLE' created"
}

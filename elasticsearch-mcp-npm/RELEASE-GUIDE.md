# Release Guide

Follow these steps to publish your Elasticsearch MCP Server to npm.

## Step 1: Build the JAR ✅ (COMPLETED)

The JAR has been built and is ready at:
```
/Users/dakshgupta/uth-ai-platforms-v2/elasticsearch-mcp-npm/mcp-server.jar
```

## Step 2: Create GitHub Release

### 2.1: Commit and push your changes

```bash
cd /Users/dakshgupta/uth-ai-platforms-v2

# Add all files
git add .

# Commit
git commit -m "Add npm package for Elasticsearch MCP Server"

# Push to GitHub
git push origin mcp-server-only
```

### 2.2: Create a Git tag

```bash
# Create and push the tag
git tag v1.0.0
git push origin v1.0.0
```

### 2.3: Create GitHub Release (Web UI)

1. Go to: https://github.com/dakshgupta/uth-ai-platforms-v2/releases/new

2. Fill in the release details:
   - **Tag version**: `v1.0.0` (should auto-select the tag you just created)
   - **Release title**: `Elasticsearch MCP Server v1.0.0`
   - **Description**:
     ```
     # Elasticsearch MCP Server v1.0.0
     
     First release of the Elasticsearch MCP Server.
     
     ## Features
     - Natural language queries to Elasticsearch
     - Automatic query building based on schema
     - Multi-host support (Primary/Secondary)
     - Date range handling
     
     ## Installation
     ```bash
     npx @dakshgupta/elasticsearch-mcp
     ```
     
     See the [README](../elasticsearch-mcp-npm/README.md) for configuration details.
     ```

3. **Attach the JAR file**:
   - Click "Attach binaries by dropping them here or selecting them"
   - Upload: `/Users/dakshgupta/uth-ai-platforms-v2/elasticsearch-mcp-npm/mcp-server.jar`
   - **IMPORTANT**: After upload, rename it to exactly `mcp-server.jar` (remove any version suffix)

4. Click "Publish release"

### 2.4: Verify the release

Check that the release is available at:
```
https://github.com/dakshgupta/uth-ai-platforms-v2/releases/tag/v1.0.0
```

And the JAR download URL should be:
```
https://github.com/dakshgupta/uth-ai-platforms-v2/releases/download/v1.0.0/mcp-server.jar
```

## Step 3: Update GitHub Username in CLI Script

**IMPORTANT**: Update the GitHub username in `bin/cli.js` if it's different:

```bash
cd /Users/dakshgupta/uth-ai-platforms-v2/elasticsearch-mcp-npm

# Edit bin/cli.js and update these lines if needed:
# const GITHUB_USER = 'dakshgupta';  // Your actual GitHub username
# const GITHUB_REPO = 'uth-ai-platforms-v2';
```

## Step 4: Publish to npm

### 4.1: Login to npm

```bash
cd /Users/dakshgupta/uth-ai-platforms-v2/elasticsearch-mcp-npm

# Login to npm (you'll need an npm account)
npm login
```

If you don't have an npm account:
1. Go to https://www.npmjs.com/signup
2. Create an account
3. Then run `npm login`

### 4.2: Publish the package

```bash
# Publish as a scoped public package
npm publish --access public
```

If you get an error about the package name, you can either:
- Use your npm username: Update `package.json` name to `@your-npm-username/elasticsearch-mcp`
- Or publish without scope: Change name to `elasticsearch-mcp-daksh` (must be unique)

### 4.3: Verify publication

Check your package at:
```
https://www.npmjs.com/package/@dakshgupta/elasticsearch-mcp
```

## Step 5: Update Your MCP Configuration

Update your `~/.cursor/mcp.json`:

```json
{
  "mcpServers": {
    "elasticsearch": {
      "command": "npx",
      "args": [
        "-y",
        "@dakshgupta/elasticsearch-mcp"
      ],
      "env": {
        "REDASH_BASE_URL": "http://10.84.84.143:5000",
        "REDASH_API_KEY": "your-redash-api-key-here"
      }
    }
  }
}
```

## Step 6: Test It!

```bash
# Test the npx command
npx @dakshgupta/elasticsearch-mcp

# Or restart Cursor to test with MCP
```

## Updating Later

When you make changes and want to release a new version:

1. Update version in `package.json`: `"version": "1.0.1"`
2. Rebuild JAR: `cd mcp-server && mvn clean package -DskipTests`
3. Copy JAR: `cp target/mcp-server-1.0.0-SNAPSHOT.jar ../elasticsearch-mcp-npm/mcp-server.jar`
4. Create new Git tag: `git tag v1.0.1 && git push origin v1.0.1`
5. Create new GitHub release with the JAR
6. Update version in `bin/cli.js`: `const VERSION = 'v1.0.1';`
7. Publish to npm: `npm publish`

## Troubleshooting

### "Package name already exists"
Change the package name in `package.json` to something unique.

### "You need to login"
Run `npm login` and enter your credentials.

### "JAR download fails"
Verify the GitHub release URL is correct and the JAR is attached.

### "403 Forbidden" when publishing
You might not have permission to publish under that scope. Use your own npm username or remove the scope.


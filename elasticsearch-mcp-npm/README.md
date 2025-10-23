# Elasticsearch MCP Server

Model Context Protocol (MCP) server for querying Elasticsearch data using natural language. Built with Spring Boot and Spring AI.

## Features

- 🔍 Natural language queries to Elasticsearch
- 📊 Automatic query building based on schema
- 🎯 Smart field mapping and value translation
- 🔄 Multi-host support (Primary/Secondary)
- 📅 Date range handling
- 🚀 Easy setup with npx

## Prerequisites

- Java 17 or higher
- Access to Elasticsearch instance
- Redash API credentials

## Quick Start

### Using with Cursor/Claude Desktop

Add to your `mcp.json` (typically at `~/.cursor/mcp.json`):

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
        "REDASH_BASE_URL": "http://your-redash-instance:5000",
        "REDASH_API_KEY": "your-api-key-here"
      }
    }
  }
}
```

### Manual Usage

```bash
# Set environment variables
export REDASH_BASE_URL="http://your-redash-instance:5000"
export REDASH_API_KEY="your-api-key"

# Run the server
npx @dakshgupta/elasticsearch-mcp
```

## Configuration

The server requires these environment variables:

| Variable | Description | Example |
|----------|-------------|---------|
| `REDASH_BASE_URL` | Base URL of your Redash instance | `http://10.84.84.143:5000` |
| `REDASH_API_KEY` | Your Redash API key | `your-redash-api-key-here` |

## Available MCP Tools

Once connected, you can use these tools:

### `es_schema`
Get the Elasticsearch schema and field mappings.

### `es_host`
Determine which Elasticsearch host(s) to query based on date range.

**Parameters:**
- `startDate`: Start date (YYYY-MM-DD)
- `endDate`: End date (YYYY-MM-DD)

### `es_indices`
Get the list of Elasticsearch indices for a date range.

**Parameters:**
- `startDate`: Start date (YYYY-MM-DD)
- `endDate`: End date (YYYY-MM-DD)

### `es_query`
Build an Elasticsearch query from natural language.

**Parameters:**
- `userPrompt`: Natural language query description
- `esSchema`: The Elasticsearch schema (from es_schema)
- `size`: Number of results (default: 10)
- `includeRecent`: Whether this is a recent/latest query
- `sortBy`: Sort specification (e.g., "txnDate:desc")

### `es_search`
Execute an Elasticsearch query and get results.

**Parameters:**
- `esQuery`: The Elasticsearch query DSL (from es_query)
- `hostInfo`: Host information (from es_host)
- `indices`: Comma-separated index names (from es_indices)

## Example Queries

```
"Get 5 recent successful transactions"
"Show me UPI payments above 1000 rupees"
"Find transactions for mobile number 917827662636"
"Get failed transactions between Jan 1 and Jan 31, 2025"
```

## Cache Management

The JAR file is cached in `~/.elasticsearch-mcp/` after first download.

To clear the cache:
```bash
npx @dakshgupta/elasticsearch-mcp --clear-cache
```

## Development

### Local Testing

```bash
# Clone the repository
git clone https://github.com/dakshgupta/uth-ai-platforms-v2.git
cd uth-ai-platforms-v2

# Build the Spring Boot JAR
cd mcp-server
mvn clean package

# Test the npm package locally
cd ../elasticsearch-mcp-npm
npm link
elasticsearch-mcp
```

## Troubleshooting

### "Java not found"
Install Java 17 or higher:
```bash
# macOS
brew install openjdk@17

# Ubuntu/Debian
sudo apt install openjdk-17-jdk
```

### "Download failed"
Ensure you have internet connectivity and the GitHub release exists.

### "Connection refused"
Check that your `REDASH_BASE_URL` is correct and accessible.

## License

MIT

## Author

Daksh Gupta

## Links

- [GitHub Repository](https://github.com/dakshgupta/uth-ai-platforms-v2)
- [MCP Documentation](https://modelcontextprotocol.io)


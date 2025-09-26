# Redash Integration for Production Elasticsearch Queries

This document describes the Redash integration implemented to bypass direct Elasticsearch connectivity issues in production environments.

## Overview

The integration provides a seamless way to execute Elasticsearch queries via Redash API in production, while maintaining direct ES connectivity for local/development environments.

## Architecture

```
Production Flow:
MCP Client → ElasticsearchQueryService → RedashClientService → Redash API → Elasticsearch

Development Flow:
MCP Client → ElasticsearchQueryService → McpClientService → MCP Server → Elasticsearch
```

## Configuration

### Environment Variables

Set these environment variables for production deployment:

```bash
# Redash Configuration
REDASH_API_KEY=your_redash_api_key_here
REDASH_BASE_URL=http://10.84.84.143:5000

# Optional (have defaults)
MCP_SERVER_URL=http://localhost:8080/api/mcp
OLLAMA_URL=http://localhost:11434
OLLAMA_MODEL=codellama
LOG_FILE=logs/mcp-client-prod.log
```

### Data Source Mapping

The following Redash data sources are configured:

- **Primary ES Host** (ID: 3) → `UTH_ES_Primary`
- **Secondary ES Host** (ID: 5) → `UTH_ES_Secondary`  
- **Tertiary ES Host** (ID: 12) → `UTH_ES_Tertiary`

## Usage

### Automatic Profile-Based Routing

The system automatically routes queries based on the active Spring profile:

- **Production Profile (`prod`)**: Routes to Redash
- **All Other Profiles**: Routes to direct MCP connection

### Query Execution

Queries are executed through the `ElasticsearchQueryService`, which handles routing transparently:

```java
@Autowired
private ElasticsearchQueryService elasticsearchQueryService;

// This will automatically route to Redash in prod, MCP in dev
Object result = elasticsearchQueryService.executeSearch(
    queryJson,     // Elasticsearch query as JSON string
    "primary",     // Host type: "primary", "secondary", or "tertiary"
    indices        // List of index patterns
);
```

### Query Format

Queries should follow this JSON structure:

```json
{
    "index": "payment-history-09-2025*",
    "query": {
        "bool": {
            "filter": [
                {
                    "term": {
                        "entityId": "1417094886"
                    }
                }
            ]
        }
    },
    "size": 10
}
```

## Testing

### Test Endpoints (Production Only)

The integration includes test endpoints available only in production profile:

#### Health Check
```bash
GET /api/test/redash/health
```

Response:
```json
{
    "redashAvailable": true,
    "mcpAvailable": true,
    "executionStrategy": "Redash (Production)"
}
```

#### Test Query
```bash
POST /api/test/redash/query
Content-Type: application/json

{
    "entityId": "1417094886",
    "hostType": "primary",
    "index": "payment-history-09-2025*"
}
```

### Manual Testing with cURL

You can test the Redash integration directly:

```bash
# Test query execution
curl -X POST http://localhost:8081/api/test/redash/query \
  -H "Content-Type: application/json" \
  -d '{
    "entityId": "1417094886",
    "hostType": "primary",
    "index": "payment-history-09-2025*"
  }'

# Check health
curl http://localhost:8081/api/test/redash/health
```

## Implementation Details

### RedashClientService

- **Profile**: Only active in `prod` profile
- **Async Execution**: Handles Redash's async query execution with polling
- **Error Handling**: Includes retry logic and fallback to MCP
- **Timeout**: Configurable timeout and polling intervals

### ElasticsearchQueryService

- **Profile Detection**: Automatically detects active profile
- **Service Routing**: Routes to appropriate service based on profile
- **Fallback**: Falls back to MCP if Redash fails
- **Monitoring**: Provides health check methods

### Query Flow

1. **Query Creation**: Create query in Redash via API
2. **Execution**: Execute query and get job ID
3. **Polling**: Poll job status with exponential backoff
4. **Results**: Retrieve and format results when job completes

### Error Handling

- **Connection Failures**: Automatic fallback to MCP service
- **Timeout Handling**: Configurable timeouts with proper error messages
- **Retry Logic**: Built-in retry for transient failures
- **Logging**: Comprehensive logging for debugging

## Deployment

### Production Deployment Steps

1. **Set Environment Variables**: Configure Redash API key and URL
2. **Activate Profile**: Set `spring.profiles.active=prod`
3. **Deploy Application**: Deploy with production configuration
4. **Verify Integration**: Use health check endpoint to verify connectivity

### Configuration Files

- `application-prod.yml`: Production-specific configuration
- Environment variables override configuration values

## Monitoring

### Logs

The integration provides detailed logging:

```
INFO  - Routing ES query to Redash for production environment
DEBUG - Created Redash query with ID: 123
DEBUG - Started Redash job with ID: abc-def-123
INFO  - Redash query execution completed in 2500ms
```

### Health Checks

Use the health endpoint to monitor:
- Redash connectivity status
- MCP connectivity status  
- Current execution strategy

## Troubleshooting

### Common Issues

1. **API Key Invalid**
   - Check `REDASH_API_KEY` environment variable
   - Verify API key in Redash settings

2. **Data Source Not Found**
   - Verify data source IDs in configuration
   - Check Redash data source configuration

3. **Query Timeout**
   - Increase `redash.timeout` configuration
   - Check Elasticsearch cluster performance

4. **Connection Refused**
   - Verify `REDASH_BASE_URL` is correct
   - Check network connectivity to Redash server

### Debug Mode

Enable debug logging for detailed troubleshooting:

```yaml
logging:
  level:
    com.paytm.mcpclient.redash: DEBUG
```

## Benefits

✅ **Network Isolation**: Bypasses direct ES connectivity issues  
✅ **Production Safety**: Only affects production environment  
✅ **Automatic Fallback**: Falls back to direct connection if needed  
✅ **Transparent Integration**: No changes needed in existing code  
✅ **Monitoring**: Built-in health checks and comprehensive logging  
✅ **Scalability**: Leverages Redash's query management and caching

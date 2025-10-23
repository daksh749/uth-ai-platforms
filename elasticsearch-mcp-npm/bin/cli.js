#!/usr/bin/env node

const { spawn } = require('child_process');
const path = require('path');
const https = require('https');
const fs = require('fs');
const { promisify } = require('util');

const mkdir = promisify(fs.mkdir);
const access = promisify(fs.access);

// GitHub release URL - UPDATE THIS after creating the release
const GITHUB_USER = 'dakshgupta';
const GITHUB_REPO = 'uth-ai-platforms-v2';
const VERSION = 'v1.0.0';
const JAR_URL = `https://github.com/${GITHUB_USER}/${GITHUB_REPO}/releases/download/${VERSION}/mcp-server.jar`;

// Local cache directory
const CACHE_DIR = path.join(require('os').homedir(), '.elasticsearch-mcp');
const JAR_PATH = path.join(CACHE_DIR, 'mcp-server.jar');

async function ensureCacheDir() {
  try {
    await access(CACHE_DIR);
  } catch {
    await mkdir(CACHE_DIR, { recursive: true });
  }
}

function downloadJar() {
  return new Promise((resolve, reject) => {
    console.log('📦 Downloading Elasticsearch MCP Server...');
    console.log(`   From: ${JAR_URL}`);
    
    const file = fs.createWriteStream(JAR_PATH);
    
    https.get(JAR_URL, (response) => {
      if (response.statusCode === 302 || response.statusCode === 301) {
        // Follow redirect
        https.get(response.headers.location, (redirectResponse) => {
          redirectResponse.pipe(file);
          file.on('finish', () => {
            file.close();
            console.log('✅ Download complete!');
            resolve();
          });
        }).on('error', (err) => {
          fs.unlink(JAR_PATH, () => {});
          reject(err);
        });
      } else {
        response.pipe(file);
        file.on('finish', () => {
          file.close();
          console.log('✅ Download complete!');
          resolve();
        });
      }
    }).on('error', (err) => {
      fs.unlink(JAR_PATH, () => {});
      console.error('❌ Download failed:', err.message);
      console.error('\nPlease ensure:');
      console.error(`1. GitHub release exists: https://github.com/${GITHUB_USER}/${GITHUB_REPO}/releases/tag/${VERSION}`);
      console.error('2. mcp-server.jar is attached to the release');
      reject(err);
    });
  });
}

async function checkJarExists() {
  try {
    await access(JAR_PATH, fs.constants.F_OK);
    return true;
  } catch {
    return false;
  }
}

async function startServer() {
  const jarExists = await checkJarExists();
  
  if (!jarExists) {
    await ensureCacheDir();
    await downloadJar();
  }
  
  console.log('🚀 Starting Elasticsearch MCP Server...');
  console.log(`   JAR: ${JAR_PATH}`);
  console.log(`   REDASH_BASE_URL: ${process.env.REDASH_BASE_URL || 'not set'}`);
  console.log('');
  
  const java = spawn('java', ['-jar', JAR_PATH], {
    stdio: 'inherit',
    env: { ...process.env }
  });
  
  java.on('error', (err) => {
    console.error('❌ Failed to start server:', err.message);
    console.error('\nPlease ensure Java is installed:');
    console.error('  java -version');
    process.exit(1);
  });
  
  java.on('close', (code) => {
    console.log(`\n👋 Server stopped with code ${code}`);
    process.exit(code);
  });
  
  // Handle graceful shutdown
  process.on('SIGINT', () => {
    console.log('\n⏹️  Stopping server...');
    java.kill('SIGTERM');
  });
}

// Check for --clear-cache flag
if (process.argv.includes('--clear-cache')) {
  console.log('🗑️  Clearing cache...');
  try {
    fs.unlinkSync(JAR_PATH);
    console.log('✅ Cache cleared!');
  } catch (err) {
    console.log('ℹ️  No cache to clear');
  }
  process.exit(0);
}

// Start the server
startServer().catch((err) => {
  console.error('❌ Error:', err.message);
  process.exit(1);
});


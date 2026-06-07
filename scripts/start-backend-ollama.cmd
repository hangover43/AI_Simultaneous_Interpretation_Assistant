@echo off
setlocal

set AI_PROVIDER=ollama
set AI_OLLAMA_BASE_URL=http://127.0.0.1:11434
set AI_OLLAMA_MODEL=qwen2.5:3b

cd /d J:\Projects\Agents\AI_Simultaneous_Interpretation_Assistant\backend

"J:\Environment\apache-maven-3.9.16\bin\mvn.cmd" spring-boot:run > target\backend-ollama.log 2> target\backend-ollama.err.log

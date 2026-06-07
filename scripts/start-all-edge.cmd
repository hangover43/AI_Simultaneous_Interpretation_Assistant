@echo off
setlocal

cd /d J:\Projects\Agents\AI_Simultaneous_Interpretation_Assistant
call scripts\launch-backend-ollama.cmd
timeout /t 3 /nobreak > nul
call scripts\launch-extension-edge.cmd

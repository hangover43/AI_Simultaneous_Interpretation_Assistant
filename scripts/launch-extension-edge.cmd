@echo off
setlocal

set PROJECT_DIR=J:\Projects\Agents\AI_Simultaneous_Interpretation_Assistant
set EXTENSION_DIR=%PROJECT_DIR%\extension
set PROFILE_DIR=%PROJECT_DIR%\.edge-test-profile

start "" msedge.exe ^
  --user-data-dir="%PROFILE_DIR%" ^
  --disable-extensions-except="%EXTENSION_DIR%" ^
  --load-extension="%EXTENSION_DIR%" ^
  https://www.youtube.com/

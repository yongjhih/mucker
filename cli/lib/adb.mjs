import { execSync, spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';

export function findAdbPath() {
  // 1. Try which adb / where adb
  try {
    const whichCmd = process.platform === 'win32' ? 'where adb' : 'which adb';
    const found = execSync(whichCmd, { stdio: ['pipe', 'pipe', 'ignore'], encoding: 'utf-8' }).trim();
    if (found) {
      return found.split('\n')[0].trim();
    }
  } catch {
    // continue searching
  }

  // 2. Try ANDROID_HOME or ANDROID_SDK_ROOT
  const envDirs = [process.env.ANDROID_HOME, process.env.ANDROID_SDK_ROOT];
  for (const dir of envDirs) {
    if (dir) {
      const adbBinary = process.platform === 'win32' ? 'adb.exe' : 'adb';
      const fullPath = path.join(dir, 'platform-tools', adbBinary);
      if (fs.existsSync(fullPath)) {
        return fullPath;
      }
    }
  }

  // 3. Common OS paths
  const home = os.homedir();
  const commonPaths = [
    path.join(home, 'Library/Android/sdk/platform-tools/adb'),
    path.join(home, 'Android/Sdk/platform-tools/adb'),
    path.join(home, 'AppData/Local/Android/Sdk/platform-tools/adb.exe'),
    '/opt/android-sdk/platform-tools/adb',
    '/usr/local/bin/adb',
    '/opt/homebrew/bin/adb',
  ];

  for (const p of commonPaths) {
    if (fs.existsSync(p)) {
      return p;
    }
  }

  return 'adb'; // fallback to standard PATH
}

export function listDevices(adbPath = findAdbPath()) {
  try {
    const output = execSync(`"${adbPath}" devices -l`, { encoding: 'utf-8' });
    const lines = output.trim().split('\n').slice(1);
    const devices = [];

    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed) continue;
      const parts = trimmed.split(/\s+/);
      const id = parts[0];
      const state = parts[1];
      if (state === 'device') {
        const modelMatch = trimmed.match(/model:(\S+)/);
        const deviceMatch = trimmed.match(/device:(\S+)/);
        devices.push({
          id,
          model: modelMatch ? modelMatch[1] : (deviceMatch ? deviceMatch[1] : 'Unknown'),
          raw: trimmed
        });
      }
    }

    return devices;
  } catch (err) {
    return [];
  }
}

export function forwardPort(port = 8080, adbPath = findAdbPath()) {
  try {
    execSync(`"${adbPath}" forward tcp:${port} tcp:${port}`, { encoding: 'utf-8' });
    return { success: true, port };
  } catch (err) {
    return { success: false, error: err.message };
  }
}

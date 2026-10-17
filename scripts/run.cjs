const fs = require("node:fs");
const path = require("node:path");
const { spawn } = require("node:child_process");
const root = path.resolve(__dirname, ".."),
  branch = process.argv[2] || "development";
if (!["development", "test", "production"].includes(branch))
  throw Error("Choose development/test/production");
const config = JSON.parse(
  fs.readFileSync(path.join(root, `.local/env-${branch}.json`)),
);
const jdks = fs.existsSync(path.join(root, ".tools"))
  ? fs
      .readdirSync(path.join(root, ".tools"))
      .filter((s) => s.startsWith("jdk-21"))
  : [];
const java = process.env.JAVA_HOME
  ? path.join(process.env.JAVA_HOME, "bin/java.exe")
  : jdks.length
    ? path.join(root, ".tools", jdks[0], "bin/java.exe")
    : "java";
const log = fs.openSync(path.join(root, ".local/server.log"), "a");
const child = spawn(
  java,
  ["-jar", path.join(root, "target/tram-sach-1.0.0.jar")],
  {
    cwd: root,
    env: { ...process.env, ...config },
    detached: true,
    windowsHide: true,
    stdio: ["ignore", log, log],
  },
);
fs.writeFileSync(path.join(root, ".local/server.pid"), String(child.pid));
child.unref();
console.log(
  `Trạm Sách: http://localhost:${config.PORT}, branch ${branch}, PID ${child.pid}`,
);

import {spawnSync} from "node:child_process";
const result=spawnSync(process.execPath,["node_modules/next/dist/bin/next","build"],{stdio:"inherit",env:{...process.env,ANALYZE:"true",NEXT_DIST_DIR:".next-build"}});
process.exit(result.status??1);

import {defineConfig,devices} from "@playwright/test";

export default defineConfig({
 testDir:"./capture-tests",workers:1,timeout:45000,expect:{timeout:15000},reporter:"list",
 use:{...devices["Desktop Chrome"],baseURL:"http://localhost:3105",timezoneId:"Asia/Qyzylorda",trace:"retain-on-failure"},
 webServer:{command:"node node_modules/next/dist/bin/next dev -p 3105",url:"http://localhost:3105/login",reuseExistingServer:!process.env.CI,timeout:120000,env:{NEXT_DIST_DIR:".next-capture",CI:"1"}},
});

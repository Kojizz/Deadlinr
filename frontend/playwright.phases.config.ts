import {defineConfig} from "@playwright/test";
export default defineConfig({testDir:"./phase-tests",timeout:60000,expect:{timeout:15000},workers:1,retries:0,reporter:"list",
use:{baseURL:process.env.PHASE_URL??"http://localhost:3000",trace:"retain-on-failure",viewport:{width:1280,height:800}}});

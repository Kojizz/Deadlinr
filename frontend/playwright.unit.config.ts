import {defineConfig} from "@playwright/test";

// Playwright already compiles TypeScript; these tests need no browser or server.
export default defineConfig({testDir:"./unit",fullyParallel:true,workers:2,reporter:"list"});

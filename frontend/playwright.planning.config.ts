import {defineConfig} from "@playwright/test";
import base from "./playwright.config";

// Uses the explicitly started production UI and disposable API; never starts
// or reuses the user's development server. Same real-auth setup as other e2e.
export default defineConfig({...base,webServer:undefined,retries:0,use:{...base.use,baseURL:"http://localhost:3106"}});

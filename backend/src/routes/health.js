import { Router } from 'express';
import { isModelIntegrated } from '../services/modelAdapter.js';
import { listSupportedCategories } from '../services/guidanceEngine.js';
import { getPipelineMetrics } from '../services/pipelineMetrics.js';

export const healthRouter = Router();

// This endpoint confirms the HTTP process is responsive; optional AI/data
// dependencies remain lazy-loaded and are not checked here.
healthRouter.get('/', (_req, res) => {
  res.status(200).json({
    status: 'ok',
    service: 'paypal-saathi-ai-api',
    version: '0.1.0',
    classifier: 'rules',
    modelIntegrated: isModelIntegrated(),
    pipelineMetrics: getPipelineMetrics(),
    supportedScreenCategories: listSupportedCategories()
  });
});

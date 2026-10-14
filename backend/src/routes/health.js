import { Router } from 'express';
import { isModelIntegrated } from '../services/modelAdapter.js';
import { listSupportedCategories } from '../services/guidanceEngine.js';
import { getPipelineMetrics } from '../services/pipelineMetrics.js';

export const healthRouter = Router();

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

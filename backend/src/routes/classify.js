import { Router } from 'express';
import { answerQuestion } from '../services/questionAnswerService.js';
import { isKnownCategory } from '../services/screenCategories.js';
import { sanitizeScreenContext } from '../services/screenContext.js';

export const classifyRouter = Router();

classifyRouter.post('/', async (req, res, next) => {
  try {
    const question = req.body?.question;
    const suppliedScreenCategory = req.body?.screenCategory;
    const screenCategory =
      suppliedScreenCategory === undefined ? 'unknown' : suppliedScreenCategory;

    if (typeof question !== 'string' || !question.trim()) {
      return res.status(400).json({
        error: 'question must be a non-empty string'
      });
    }

    if (typeof screenCategory !== 'string' || !isKnownCategory(screenCategory)) {
      return res.status(400).json({
        error: 'screenCategory must be a supported screen category'
      });
    }

    if (question.length > 500) {
      return res.status(413).json({
        error: 'question must be 500 characters or fewer'
      });
    }

    let screenContext;
    try {
      screenContext = sanitizeScreenContext(
        req.body?.screenContext,
        screenCategory
      );
    } catch {
      return res.status(400).json({
        error: 'screenContext must contain only recent, supported accessibility metadata'
      });
    }

    const requestController = new AbortController();
    const abortRequest = () => requestController.abort();
    const abortOnClose = () => {
      if (!res.writableEnded) {
        abortRequest();
      }
    };
    req.once('aborted', abortRequest);
    res.once('close', abortOnClose);

    let result;
    try {
      result = await answerQuestion({
        question,
        screenCategory,
        screenContext,
        signal: requestController.signal
      });
    } finally {
      req.off('aborted', abortRequest);
      res.off('close', abortOnClose);
    }

    return res.status(200).json({
      category: result.category,
      guidance: result.guidance,
      disclaimer: result.disclaimer,
      matchedBy: result.matchedBy,
      screenCategory
    });
  } catch (error) {
    return next(error);
  }
});

import { Router } from 'express';
import { classifyHelpQuestion } from '../services/questionClassifier.js';

export const classifyRouter = Router();

classifyRouter.post('/', (req, res, next) => {
  try {
    const question = req.body?.question;

    if (typeof question !== 'string' || !question.trim()) {
      return res.status(400).json({
        error: 'question must be a non-empty string'
      });
    }

    if (question.length > 500) {
      return res.status(413).json({
        error: 'question must be 500 characters or fewer'
      });
    }

    const result = classifyHelpQuestion(question);

    return res.status(200).json({
      category: result.category,
      guidance: result.guidance,
      disclaimer: 'Prototype guidance only. Verify details in PayPal yourself.'
    });
  } catch (error) {
    return next(error);
  }
});

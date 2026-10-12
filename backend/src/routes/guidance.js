import { Router } from "express";
import { getGuidance } from "../services/guidanceEngine.js";

export const guidanceRouter = Router();

guidanceRouter.post("/", (req, res) => {
  const screenCategory = req.body?.screenCategory;

  if (
    typeof screenCategory !== "string" ||
    screenCategory.trim().length === 0
  ) {
    return res.status(400).json({
      error: "screenCategory must be a non-empty string"
    });
  }

  if (screenCategory.length > 80) {
    return res.status(413).json({
      error: "screenCategory must be 80 characters or fewer"
    });
  }

  const result = getGuidance(screenCategory);

  return res.status(200).json({
    ...result,
    safetyNote:
      "Guidance only. Verify all details in PayPal yourself. This API does not verify or perform transactions."
  });
});

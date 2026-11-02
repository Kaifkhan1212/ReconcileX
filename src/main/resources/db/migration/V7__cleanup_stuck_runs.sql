-- V7: Mark stuck RUNNING reconciliation runs as FAILED
-- Runs that have been in RUNNING status for more than 10 minutes are stuck
-- (caused by synchronous HTTP timeout on Render free tier before async was introduced).
-- The StaleRunReaper service now handles this automatically going forward.

UPDATE reconciliation_runs
SET    status       = 'FAILED',
       completed_at = NOW()
WHERE  status     = 'RUNNING'
  AND  started_at < NOW() - INTERVAL '10 minutes';


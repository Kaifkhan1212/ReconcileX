package com.reconcilex.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reconcilex.config.MatchingProperties;
import com.reconcilex.dto.ReconciliationRunSummaryResponse;
import com.reconcilex.entity.BankStatementRow;
import com.reconcilex.entity.InternalTransaction;
import com.reconcilex.entity.ReconciliationResultType;
import com.reconcilex.entity.ReconciliationRunStatus;
import com.reconcilex.repository.BankStatementRowRepository;
import com.reconcilex.repository.InternalTransactionRepository;
import com.reconcilex.rules.MatchOutcome;
import com.reconcilex.rules.ReconciliationContext;
import com.reconcilex.rules.ReconciliationRuleEngine;

@Service
public class ReconciliationExecutionService {

    private final InternalTransactionRepository internalTransactionRepository;
    private final BankStatementRowRepository bankStatementRowRepository;
    private final ReconciliationRuleEngine ruleEngine;
    private final ReconciliationBatchInsertService batchInsertService;
    private final MatchingProperties matchingProperties;

    public ReconciliationExecutionService(
            InternalTransactionRepository internalTransactionRepository,
            BankStatementRowRepository bankStatementRowRepository,
            ReconciliationRuleEngine ruleEngine,
            ReconciliationBatchInsertService batchInsertService,
            MatchingProperties matchingProperties
    ) {
        this.internalTransactionRepository = internalTransactionRepository;
        this.bankStatementRowRepository = bankStatementRowRepository;
        this.ruleEngine = ruleEngine;
        this.batchInsertService = batchInsertService;
        this.matchingProperties = matchingProperties;
    }

    @Transactional
    public ReconciliationRunSummaryResponse executeAndSave(Long runId, Long bankImportId, Long internalImportId) {
        List<InternalTransaction> internalTransactions =
                internalTransactionRepository.findByFileImport_IdOrderBySourceRowNumberAsc(internalImportId);
        List<BankStatementRow> bankRows =
                bankStatementRowRepository.findByFileImport_IdOrderBySourceRowNumberAsc(bankImportId);

        ReconciliationContext context = new ReconciliationContext(
                internalTransactions,
                bankRows,
                matchingProperties.getDateToleranceDays()
        );

        ruleEngine.execute(context);

        List<MatchOutcome> outcomes = context.getOutcomes();
        batchInsertService.insertReconciliationResults(runId, outcomes);

        Map<ReconciliationResultType, Long> counts = new EnumMap<>(ReconciliationResultType.class);
        for (MatchOutcome outcome : outcomes) {
            counts.put(outcome.getResultType(), counts.getOrDefault(outcome.getResultType(), 0L) + 1L);
        }

        return new ReconciliationRunSummaryResponse(
                runId,
                ReconciliationRunStatus.COMPLETED,
                counts,
                context.getUnmatchedSuccessInternalCount(),
                context.getUnmatchedBankCount(),
                context.getUnprocessedBankDebitsCount()
        );
    }
}

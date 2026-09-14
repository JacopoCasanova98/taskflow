package com.taskflow.shared.logging;

import org.slf4j.Logger;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Emit only after commit; capture scalar identifiers, never managed entities or user content. */
public final class MutationLog {
	private MutationLog() {}

	public static void afterCommit(Logger logger, String message, Object... identifiers) {
		if (TransactionSynchronizationManager.isActualTransactionActive()
				&& TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					logger.info(message, identifiers);
				}
			});
		} else {
			logger.info(message, identifiers);
		}
	}
}

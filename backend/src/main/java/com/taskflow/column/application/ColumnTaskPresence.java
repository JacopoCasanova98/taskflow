package com.taskflow.column.application;

import java.util.UUID;

/** Checked under the parent Board mutation lock before direct Column deletion. */
public interface ColumnTaskPresence {
	boolean hasTasks(UUID columnId);
}

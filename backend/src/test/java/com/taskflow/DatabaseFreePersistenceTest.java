package com.taskflow;

import com.taskflow.auth.persistence.RefreshTokenRepository;
import com.taskflow.board.persistence.BoardRepository;
import com.taskflow.column.persistence.ColumnRepository;
import com.taskflow.user.persistence.UserRepository;
import com.taskflow.task.persistence.TaskRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

/** Repository/transaction collaborators for the deliberately database-free test profile. */
public abstract class DatabaseFreePersistenceTest {
	@MockitoBean
	protected TaskRepository tasks;
	@MockitoBean
	protected ColumnRepository columns;
	@MockitoBean
	protected BoardRepository boards;
	@MockitoBean
	protected UserRepository users;
	@MockitoBean
	protected RefreshTokenRepository sessions;
	@MockitoBean
	protected PlatformTransactionManager transactionManager;
}

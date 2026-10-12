package com.ledgerflow.ledger.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccountJpaRepository extends JpaRepository<AccountEntity, UUID> {}

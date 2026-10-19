package com.diyarsari.transfer.repository;

import com.diyarsari.transfer.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AccountRepository extends JpaRepository<Account, Long> {

    /** Loads accounts in ascending id order, the same order in which their rows get locked on flush. */
    List<Account> findAllByIdInOrderByIdAsc(Collection<Long> ids);
}

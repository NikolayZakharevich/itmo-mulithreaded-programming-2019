package fgbank;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * Bank implementation.
 */
public class BankImpl implements Bank {
    /**
     * An array of accounts by index.
     */
    private final Account[] accounts;

    private final Map<Account, Integer> accountIds;

    /**
     * Creates new bank instance.
     *
     * @param n the number of accounts (numbered from 0 to n-1).
     */
    public BankImpl(int n) {
        accounts = new Account[n];
        accountIds = new HashMap<>(n);
        for (int i = 0; i < n; i++) {
            Account account = new Account();
            accounts[i] = account;
            accountIds.put(account, i);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumberOfAccounts() {
        return accounts.length;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getAmount(int index) {
        return accounts[index].amount;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getTotalAmount() {
        startOperation(accounts);
        long sum = 0;
        for (Account account : accounts) {
            sum += account.amount;
        }
        finishOperation(accounts);
        return sum;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long deposit(int index, long amount) {
        if (amount <= 0)
            throw new IllegalArgumentException("Invalid amount: " + amount);
        Account account = accounts[index];
        startOperation(account);
        if (amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT) {
            finishOperation(account);
            throw new IllegalStateException("Overflow");
        }
        account.amount += amount;
        long result = account.amount;
        finishOperation(account);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long withdraw(int index, long amount) {
        if (amount <= 0)
            throw new IllegalArgumentException("Invalid amount: " + amount);
        Account account = accounts[index];
        startOperation(account);
        if (account.amount - amount < 0) {
            finishOperation(account);
            throw new IllegalStateException("Underflow");
        }
        account.amount -= amount;
        long result = account.amount;
        finishOperation(account);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void transfer(int fromIndex, int toIndex, long amount) {
        if (amount <= 0)
            throw new IllegalArgumentException("Invalid amount: " + amount);
        if (fromIndex == toIndex)
            throw new IllegalArgumentException("fromIndex == toIndex");
        Account from = accounts[fromIndex];
        Account to = accounts[toIndex];
        startOperation(from, to);
        if (amount > from.amount) {
            finishOperation(from, to);
            throw new IllegalStateException("Underflow");
        } else if (amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT) {
            finishOperation(from, to);
            throw new IllegalStateException("Overflow");
        }
        from.amount -= amount;
        to.amount += amount;
        finishOperation(from, to);
    }

    private void startOperation(Account... accounts) {
        sortById(false, accounts).forEach(account -> account.lock.lock());
    }

    private void finishOperation(Account... accounts) {
        sortById(true, accounts).forEach(account -> account.lock.unlock());
    }

    private Stream<Account> sortById(boolean reversed, Account... accounts) {
        Comparator<Account> comparator = Comparator.comparingInt(accountIds::get);
        return Arrays.stream(accounts).sorted(reversed ? comparator.reversed() : comparator);
    }

    /**
     * Private account data structure.
     */
    private static class Account {
        /**
         * Amount of funds in this account.
         */
        long amount;

        Lock lock = new ReentrantLock();
    }
}

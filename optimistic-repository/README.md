Optimistic Repository
=====================
 
 The Optimistic Repository provides concurrent optimistic
 transactions for embedded Sesame stores. It can replace the
 SailRepository within a Sesame configuration. When the AliBaba JARs are
 included in the console, additional configuration templates can be used
 with the create command, including optimistic-memory and
 optimistic-native (among others). The configuration templates that
 create ObjectRepositories also include the optimistic repository.

 The optimistic repository is designed for small concurrent transactions.
 Although it also works for larger transactions, you may want to disable read
 snapshot during large bulk-load transactions. Serializable or snapshot
 transactions may throw a ConcurrencyException on commit, if the
 transaction cannot be committed, due to state changes within the
 repository. Applications that utilize the optimistic repository in serializable
 or snapshot mode must be
 prepared for a ConcurrencyException when a transaction is committed.

 Sesame 2.x does not support transaction isolation across HTTP and
 therefore the optimistic repository has no effect on remote clients and
 is only useful with embedded repositories.

 The OptimisticRepository has four incremental modes of operation. The default
 mode (read snapshot) is similar to what is provided by the SailRepository, but
 the OptimisticRepository allows concurrent writes. Each transaction occurs in
 isolation and is only observable by itself until the change is committed.
 SPARQL 1.1 update statements will read the uncommitted state of the RDF store
 at the time the update is executed, when using the OptimisticRepository. When
 read snapshot is disabled, OptimisticRepository behaves just like the SailRepository.

 When snapshot is enabled the RepositoryConnection#commit() and
 RepositoryConnection#setAutoCommit(boolean) methods way throw a
 ConcurrencyException if the transaction read a state of the store, that was not
 present when the transaction started, which can lead to a phantom read. A
 phantom read is when two read operations in the same transaction observe
 different states of the store.

 When serializable is enabled a ConcurrencyException may also be thrown if the
 observed state of a transaction changes before it is committed, possibly
 leaving the state of the store inconsistent. The observed state is any graph
 pattern read during the transaction.

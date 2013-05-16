Optimistic SAIL
=====================
 
 The Optimistic SAIL provides concurrent optimistic
 transactions for embedded Sesame stores. It can wrap the
 NativeStore and MemoryStore within a Sesame configuration. When the AliBaba JARs are
 included in the console, additional configuration templates can be used
 with the create command, including optimistic-memory and
 optimistic-native (among others). The configuration templates that
 create ObjectRepositories also include the optimistic SAIL.

 The Optimistic SAIL is designed for small concurrent transactions.
 Although it also works for larger transactions, you may want to disable read
 snapshot during large bulk-load transactions. Serializable or snapshot
 transactions may throw a ConcurrencyException on commit, if the
 transaction cannot be committed, due to state changes within the
 SAIL. Applications that utilize the optimistic SAIL in serializable
 or snapshot mode must be
 prepared for a SailConflictException when a transaction is committed.

 Sesame 2.x does not support transaction isolation across HTTP and
 therefore the optimistic SAIL has no effect on remote clients and
 is only useful with embedded repositories.

 The OptimisticSail has four incremental modes of operation. The default
 mode (read snapshot) is similar to what is provided by the NativeStore/MemoryStore, but
 the OptimisticSail allows concurrent writes. Each transaction occurs in
 isolation and is only observable by itself until the change is committed.
 SPARQL 1.1 update statements will read the uncommitted state of the RDF store
 at the time the update is executed, when using the OptimisticSail. When
 read snapshot is disabled, OptimisticSail behaves just like the underlying store.

 When snapshot is enabled the SailConnection#commit() methods may throw a
 SailConflictException if the transaction read a state of the store, that was not
 present when the transaction started, which can lead to a phantom read. A
 phantom read is when two read operations in the same transaction observe
 different states of the store.

 When serializable is enabled a SailConflictException may also be thrown if the
 observed state of a transaction changes before it is committed, possibly
 leaving the state of the store inconsistent. The observed state is any graph
 pattern read during the transaction.

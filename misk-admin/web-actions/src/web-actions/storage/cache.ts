const DB_NAME = 'networkCacheDB';
const STORE_NAME = 'responses_v2';
const DB_VERSION = 2;
const CACHE_DURATION_MS = 1000 * 60 * 60;

function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME);
      }
    };

    request.onsuccess = () => {
      resolve(request.result);
    };

    request.onerror = () => {
      reject(request.error);
    };
  });
}

function fetch<T>(store: IDBObjectStore, key: string): Promise<T | undefined> {
  return new Promise<T | undefined>((resolve, reject) => {
    const request = store.get(key);
    request.onerror = () => reject(request.error);
    request.onsuccess = () => resolve(request.result);
  });
}

function insert<T>(
  store: IDBObjectStore,
  key: string,
  value: T,
): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    const request = store.put(value, key);
    request.onerror = () => reject(request.error);
    request.onsuccess = () => resolve();
  });
}

function openTransaction(
  db: IDBDatabase,
  mode: IDBTransactionMode,
): IDBTransaction {
  const transaction = db.transaction(STORE_NAME, mode);
  transaction.onerror = () => console.error(transaction.error);
  return transaction;
}

const inflight = new Map<string, Promise<any>>();

interface CacheEntry<T> {
  lastUpdatedAtMs: number;
  value: T;
}

export async function cachedResponse<T>(
  key: string,
  fn: () => Promise<T>,
): Promise<T> {
  const db = await openDatabase();
  try {
    const readTransaction = openTransaction(db, 'readonly');

    const existing = await fetch<CacheEntry<T>>(
      readTransaction.objectStore(STORE_NAME),
      key,
    );

    if (existing && Date.now() - existing.lastUpdatedAtMs < CACHE_DURATION_MS) {
      return existing.value;
    }

    if (inflight.has(key)) {
      return inflight.get(key);
    }

    const networkCall = fn();
    inflight.set(key, networkCall);

    const response = await networkCall;
    const writeTransaction = openTransaction(db, 'readwrite');

    await insert<CacheEntry<T>>(writeTransaction.objectStore(STORE_NAME), key, {
      lastUpdatedAtMs: Date.now(),
      value: response,
    });
    return response;
  } finally {
    inflight.delete(key);
    db.close();
  }
}

export async function clearCache(): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    const request = indexedDB.deleteDatabase(DB_NAME);
    request.onerror = () => reject(request.error);
    request.onsuccess = () => resolve();
  });
}

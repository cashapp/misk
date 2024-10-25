import { cachedResponse } from "@misk-console/storage/cache"

export async function fetchCached<T>(url: string): Promise<T> {
  return cachedResponse<T>(url, async () => {
    const response = await fetch(url)
    if (!response.ok) {
      throw new Error(await response.text())
    }
    return await response.json()
  })
}

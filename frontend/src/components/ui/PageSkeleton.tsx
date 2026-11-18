
export const PageSkeleton = () => {
  return (
    <main className="w-full bg-surface min-h-screen px-margin-sm md:px-margin-lg py-margin">
      <div className="flex flex-col w-full">
        {/* Page Header & Breadcrumb Section Skeleton */}
        <div className="mb-margin">
          <div className="flex items-center gap-2 mb-2">
            <div className="w-24 h-3 bg-surface-dim animate-pulse rounded"></div>
            <div className="w-3 h-3 bg-surface-dim animate-pulse rounded"></div>
            <div className="w-32 h-3 bg-surface-dim animate-pulse rounded"></div>
          </div>
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md">
            <div className="space-y-2">
              <div className="w-64 h-8 bg-surface-dim animate-pulse rounded"></div>
              <div className="w-96 h-4 bg-surface-dim animate-pulse rounded mt-1"></div>
            </div>
            <div className="flex items-center gap-space-sm">
              <div className="h-10 w-32 bg-surface-container animate-pulse rounded-lg"></div>
              <div className="h-10 w-28 bg-primary/20 animate-pulse rounded-lg"></div>
            </div>
          </div>
        </div>

        {/* Main Dynamic Sections Container */}
        <div className="space-y-margin">
          {/* KPI Cards Grid Skeleton */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-gutter-lg">
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="p-space-lg bg-surface-container-lowest rounded-xl shadow-[0_1px_3px_0_rgba(15,23,42,0.05)] relative overflow-hidden">
                <div className="flex items-center justify-between mb-4">
                  <div className="w-10 h-10 rounded-lg bg-surface-dim animate-pulse"></div>
                  <div className="w-16 h-5 rounded-full bg-surface-dim animate-pulse"></div>
                </div>
                <div className="space-y-2">
                  <div className="w-24 h-4 bg-surface-dim animate-pulse rounded"></div>
                  <div className="w-36 h-8 bg-surface-dim animate-pulse rounded"></div>
                </div>
                <div className="mt-4 pt-4 border-t border-outline-variant/20 flex items-center justify-between">
                  <div className="w-28 h-3 bg-surface-dim animate-pulse rounded"></div>
                  <div className="w-12 h-3 bg-surface-dim animate-pulse rounded"></div>
                </div>
              </div>
            ))}
          </div>

          {/* Data Records Table Skeleton */}
          <div className="bg-surface-container-lowest rounded-xl shadow-[0_1px_3px_0_rgba(15,23,42,0.05)] overflow-hidden">
            {/* Table Header Action Bar */}
            <div className="p-space-md border-b border-outline-variant/20 flex flex-col sm:flex-row sm:items-center justify-between gap-space-sm">
              <div className="w-48 h-9 bg-surface-dim animate-pulse rounded-lg"></div>
              <div className="flex items-center gap-space-sm">
                <div className="w-28 h-9 bg-surface-dim animate-pulse rounded-lg"></div>
                <div className="w-32 h-9 bg-surface-dim animate-pulse rounded-lg"></div>
              </div>
            </div>
            
            {/* Table Body */}
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-surface-container-low h-10 border-b border-outline-variant/20">
                    <th className="px-space-md py-2"><div className="w-24 h-3 bg-surface-dim animate-pulse rounded"></div></th>
                    <th className="px-space-md py-2"><div className="w-36 h-3 bg-surface-dim animate-pulse rounded"></div></th>
                    <th className="px-space-md py-2"><div className="w-28 h-3 bg-surface-dim animate-pulse rounded"></div></th>
                    <th className="px-space-md py-2"><div className="w-20 h-3 bg-surface-dim animate-pulse rounded"></div></th>
                    <th className="px-space-md py-2 text-right"><div className="w-16 h-3 bg-surface-dim animate-pulse rounded ml-auto"></div></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant/10">
                  {[1, 2, 3, 4, 5].map((row) => (
                    <tr key={row} className="h-14">
                      <td className="px-space-md py-3"><div className="w-28 h-4 bg-surface-dim animate-pulse rounded"></div></td>
                      <td className="px-space-md py-3">
                        <div className="space-y-1.5">
                          <div className={`w-${row % 2 === 0 ? '48' : '56'} h-4 bg-surface-dim animate-pulse rounded`}></div>
                          <div className={`w-${row % 2 === 0 ? '32' : '28'} h-3 bg-surface-dim animate-pulse rounded`}></div>
                        </div>
                      </td>
                      <td className="px-space-md py-3"><div className="w-32 h-4 bg-surface-dim animate-pulse rounded"></div></td>
                      <td className="px-space-md py-3"><div className="w-20 h-5 bg-surface-dim animate-pulse rounded-full"></div></td>
                      <td className="px-space-md py-3 text-right"><div className="w-8 h-8 bg-surface-dim animate-pulse rounded-lg ml-auto"></div></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {/* Pagination Skeleton */}
            <div className="p-space-md border-t border-outline-variant/20 flex items-center justify-between">
              <div className="w-32 h-4 bg-surface-dim animate-pulse rounded"></div>
              <div className="flex items-center gap-space-xs">
                <div className="w-8 h-8 bg-surface-dim animate-pulse rounded-lg"></div>
                <div className="w-8 h-8 bg-surface-dim animate-pulse rounded-lg"></div>
                <div className="w-8 h-8 bg-surface-dim animate-pulse rounded-lg"></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  );
};

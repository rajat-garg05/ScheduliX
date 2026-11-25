import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell, Check, CheckCheck, Loader2 } from 'lucide-react';
import api, { getErrorMessage } from '../api/client';

function formatTimestamp(isoString) {
  if (!isoString) return '';
  return new Date(isoString).toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export default function NotificationsPage() {
  const [page, setPage] = useState(0);
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['notifications', 'list', page],
    queryFn: async () => {
      const res = await api.get('/notifications', { params: { page, size: 50 } });
      return res.data;
    },
  });

  const markReadMutation = useMutation({
    mutationFn: (id) => api.post(`/notifications/${id}/read`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  const markAllReadMutation = useMutation({
    mutationFn: () => api.post('/notifications/read-all'),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  const items = data?.items || [];
  const totalPages = data?.totalPages || 0;
  const hasUnread = items.some((n) => !n.readAt);

  function handleOpenNotification(item) {
    if (!item.readAt) {
      markReadMutation.mutate(item.id);
    }
    if (item.link) {
      navigate(item.link);
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Notifications</h1>
          <p className="mt-1 text-sm text-slate-600">
            Messages about leave requests, substitutes, and schedule updates.
          </p>
        </div>

        {hasUnread && (
          <button
            type="button"
            onClick={() => markAllReadMutation.mutate()}
            disabled={markAllReadMutation.isPending}
            className="inline-flex items-center gap-1.5 rounded-md border border-slate-300 bg-white px-3.5 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60"
          >
            <CheckCheck className="h-4 w-4" />
            <span>Mark all as read</span>
          </button>
        )}
      </div>

      {isLoading && (
        <div className="rounded-lg border border-slate-200 bg-white p-8 flex items-center justify-center gap-2 text-sm text-slate-600">
          <Loader2 className="h-4 w-4 animate-spin text-blue-700" />
          <span>Loading notifications...</span>
        </div>
      )}

      {isError && (
        <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {getErrorMessage(error, 'Could not load notifications.')}
        </div>
      )}

      {!isLoading && !isError && items.length === 0 && (
        <div className="rounded-lg border border-slate-200 bg-white p-10 text-center">
          <Bell className="h-7 w-7 text-slate-400 mx-auto" />
          <p className="mt-3 text-sm font-medium text-slate-900">No notifications yet</p>
          <p className="mt-1 text-sm text-slate-500">
            Leave requests and substitute updates will appear here.
          </p>
        </div>
      )}

      {!isLoading && !isError && items.length > 0 && (
        <div className="rounded-lg border border-slate-200 bg-white divide-y divide-slate-200">
          {items.map((item) => {
            const isUnread = !item.readAt;
            return (
              <div
                key={item.id}
                className={`px-4 py-3.5 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 ${
                  isUnread ? 'bg-blue-50/40' : 'bg-white'
                }`}
              >
                <div className="flex items-start gap-3 min-w-0">
                  <span
                    className={`mt-1.5 h-2 w-2 rounded-full shrink-0 ${
                      isUnread ? 'bg-blue-700' : 'bg-transparent'
                    }`}
                  />
                  <div className="min-w-0">
                    {item.link ? (
                      <button
                        type="button"
                        onClick={() => handleOpenNotification(item)}
                        className="text-left text-sm font-medium text-slate-900 hover:text-blue-700"
                      >
                        {item.text}
                      </button>
                    ) : (
                      <p className="text-sm text-slate-900">{item.text}</p>
                    )}
                    <p className="mt-0.5 text-xs text-slate-500">
                      {formatTimestamp(item.createdAt)}
                    </p>
                  </div>
                </div>

                {isUnread && (
                  <button
                    type="button"
                    onClick={() => markReadMutation.mutate(item.id)}
                    disabled={markReadMutation.isPending}
                    className="inline-flex items-center gap-1 self-start sm:self-center rounded-md border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 shrink-0"
                  >
                    <Check className="h-3.5 w-3.5" />
                    <span>Mark as read</span>
                  </button>
                )}
              </div>
            );
          })}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-between text-sm text-slate-600">
          <span>
            Page {page + 1} of {totalPages}
          </span>
          <div className="flex gap-2">
            <button
              type="button"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
            >
              Previous
            </button>
            <button
              type="button"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
              className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
            >
              Next
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
import Link from 'next/link';

function DariLogo() {
  return (
    <div className="rounded-2xl bg-gray-100 p-5 dark:bg-neutral-800">
      <svg width="48" height="48" viewBox="0 0 960 960" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path
          d="M280,800 L80,600l200,-200 56,57 -103,103h287v80L233,640l103,103 -56,57ZM680,560 L624,503 727,400L440,400v-80h287L624,217l56,-57 200,200 -200,200Z"
          className="fill-gray-800 dark:fill-gray-200"
        />
      </svg>
    </div>
  );
}

export default function HomePage() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-8 bg-white px-4 text-center dark:bg-neutral-950">
      <DariLogo />
      <h1 className="text-7xl font-bold tracking-tight">Dari</h1>
      <p className="max-w-xl text-xl text-gray-500 dark:text-gray-400">
        A Chucker-inspired WebView bridge communication inspector for Android.
      </p>
      <div className="flex gap-4">
        <Link
          href="/docs"
          className="rounded-xl bg-black px-10 py-4 text-base font-semibold text-white transition-opacity hover:opacity-70 dark:bg-white dark:text-black"
        >
          Get Started
        </Link>
        <Link
          href="https://github.com/easyhooon/dari"
          className="rounded-xl border border-gray-300 px-10 py-4 text-base font-semibold transition-colors hover:bg-gray-100 dark:border-gray-700 dark:hover:bg-gray-800"
          target="_blank"
          rel="noopener noreferrer"
        >
          GitHub
        </Link>
      </div>
    </main>
  );
}

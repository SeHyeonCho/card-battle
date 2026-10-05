import { AnimatePresence, motion } from 'motion/react'
import type { TurnBanner as Banner } from '../store/app'

/**
 * 차례 전환 연출 (PRD FR-GAME-10). 화면 가운데에 "누구의 차례"를 크게 띄운다.
 * 내 차례면 연두색, 남의 차례면 어두운 칸. 클릭을 막지 않아서 이 동안에도 카드를 고를 수 있다
 */
export function TurnBanner({ banner, viewerId, nickOf }: { banner: Banner | null; viewerId: string; nickOf: (playerId: string) => string }) {
  return (
    <div className="pointer-events-none fixed inset-0 z-40 flex items-center justify-center">
      <AnimatePresence>
        {banner && (
          <motion.div
            key={banner.id}
            initial={{ opacity: 0, scaleX: 0.2 }}
            animate={{ opacity: 1, scaleX: 1 }}
            exit={{ opacity: 0, x: 120, transition: { duration: 0.2 } }}
            transition={{ type: 'spring', stiffness: 320, damping: 26 }}
            className={`rounded-3xl px-12 py-5 text-center shadow-[0_24px_60px_-20px_#000] ${
              banner.playerId === viewerId ? 'bg-accent text-on-accent' : 'bg-raised text-ink'
            }`}
          >
            {banner.playerId === viewerId ? (
              <>
                <div className="text-4xl font-extrabold tracking-tight">내 차례</div>
                <div className="mt-1 text-sm font-medium opacity-70">카드를 골라 두면 곧바로 냅니다</div>
              </>
            ) : (
              <div className="text-3xl font-extrabold tracking-tight">{nickOf(banner.playerId)}의 차례</div>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

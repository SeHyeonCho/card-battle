import { AnimatePresence, motion } from 'motion/react'
import type { TurnBanner as Banner } from '../store/app'

/**
 * 차례 전환 연출 (PRD FR-GAME-10). 화면 가운데에 "누구의 차례"를 크게 띄운다.
 * 내 차례면 금색 틀, 남의 차례면 파란 틀. 클릭을 막지 않아서 이 동안에도 카드를 고를 수 있다
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
            className={`frame ${banner.playerId === viewerId ? 'frame-gold' : 'frame-blue'} px-10 py-4 text-center`}
          >
            {banner.playerId === viewerId ? (
              <>
                <div className="neon-gold text-4xl font-bold">▶ 내 차례!</div>
                <div className="mt-1 text-sm text-amber-100 text-outline">카드를 골라 두면 곧바로 냅니다</div>
              </>
            ) : (
              <div className="neon-blue text-3xl font-bold">{nickOf(banner.playerId)}의 차례</div>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

package com.inmc.drops.config

import com.inmc.drops.util.Ph
import kr.inmc.core.config.MessageCatalog
import org.bukkit.configuration.file.YamlConfiguration

/**
 * `messages.yml` 한 벌. 읽고 보내는 부분은 core 의 [MessageCatalog] 가 갖고 있고 여기는 기본값 표뿐이다.
 * `ResourceTest` 가 배포 파일과 이 표의 키가 정확히 같은지, 코드가 부르는 키가 전부 있는지 지킨다.
 */
class Messages(values: Map<String, String>) : MessageCatalog<Ph>(values, DEFAULTS) {

    companion object {

        fun from(config: YamlConfiguration): Messages = Messages(merge(DEFAULTS, config))

        val DEFAULTS: Map<String, String> = linkedMapOf(
            PREFIX to "<gradient:#a8e063:#56ab2f>[ 드랍 ]</gradient> ",

            // --- 공통 ---------------------------------------------------------------
            "player-only" to "<red>플레이어만 쓸 수 있습니다.</red>",
            "no-permission" to "<red>권한이 없습니다.</red>",
            "reloaded" to "<green>다시 불러왔습니다. 설정된 표 {count}개.</green>",

            // --- 드랍 ---------------------------------------------------------------
            "announce" to "<gold>★</gold> <yellow>{player}</yellow>님이 <aqua>{source}</aqua>에서 <white>{item}</white>을(를) 얻었습니다!",

            // --- 정보 ---------------------------------------------------------------

            // --- 이벤트 배율 ----------------------------------------------------------
            "boost-started" to "<gold>드랍 이벤트!</gold> <yellow>{value}</yellow> 동안 모든 커스텀 드랍 확률이 <yellow>×{amount}</yellow> 입니다.",
            "boost-stopped" to "<gray>드랍 이벤트를 끝냈습니다.</gray>",
            "boost-ended" to "<gray>드랍 이벤트가 끝났습니다.</gray>",
            "boost-none" to "<gray>진행 중인 드랍 이벤트가 없습니다.</gray>",
            "boost-bar" to "<gold>드랍 이벤트 ×{amount}</gold> <gray>— 남은 시간 {value}</gray>",

            // --- 관리 화면 -------------------------------------------------------------
            "entries-added" to "<green>항목 {count}개를 등록했습니다.</green>",
            "entries-removed" to "<yellow>항목 {count}개를 지웠습니다.</yellow>",
            "blocks-added" to "<green>블록 표 {count}개를 만들었습니다.</green>",
            "targets-added" to "<green>대상 블록 {count}개를 넣었습니다.</green>",
            "block-taken" to "<yellow>{item}<yellow> 은(는) 이미 '{source}' 표에 있습니다.</yellow>",
            "not-a-block" to "<yellow>{item}<yellow> 은(는) 놓을 수 있는 블록이 아니라 건너뜁니다.</yellow>",
            "crops-added" to "<green>작물 {count}개를 추가했습니다.</green>",
            "crop-known" to "<yellow>{item}<yellow> 은(는) 이미 작물 목록에 있습니다.</yellow>",
            "table-deleted" to "<yellow>'{source}' 표를 지웠습니다.</yellow>",
            "last-target" to "<red>대상 블록이 하나뿐이라 뺄 수 없습니다. 표를 지우세요.</red>",

            // --- 검증 ---------------------------------------------------------------
            "verify-done" to "<gold>드랍 검증</gold> <gray>— 통과 <green>{amount}</green> · 실패 <red>{count}</red></gray>",
            "verify-failure" to "<red> ✘ {item}</red> <gray>— {value}</gray>",
            "verify-report" to "<gray>결과 파일: <white>{value}</white></gray>",

            // --- 도움말 -------------------------------------------------------------
            "help" to listOf(
                "<gold>/드랍 정보</gold> <gray>- 몹·작물·블록에서 나오는 것</gray>",
                "<red>/드랍</red> <gray>- 관리 화면</gray>",
                "<red>/드랍 배율 <배율> <분></red> <gray>· </gray><red>/드랍 배율 끄기</red> <gray>- 드랍 이벤트</gray>",
                "<red>/드랍 리로드</red> <gray>· </gray><red>/드랍 검증</red>",
            ).joinToString("\n"),
        )
    }
}

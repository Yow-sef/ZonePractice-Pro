package dev.nandi0813.practice.manager.fight.match.type.playersvsplayers.partysplit;

import dev.nandi0813.practice.manager.arena.arenas.Arena;
import dev.nandi0813.practice.manager.backend.LanguageManager;
import dev.nandi0813.practice.manager.fight.match.enums.MatchType;
import dev.nandi0813.practice.manager.fight.match.enums.TeamEnum;
import dev.nandi0813.practice.manager.fight.match.type.playersvsplayers.PlayersVsPlayers;
import dev.nandi0813.practice.manager.ladder.abstraction.Ladder;
import dev.nandi0813.practice.manager.party.Party;
import dev.nandi0813.practice.manager.nametag.NametagManager;
import dev.nandi0813.practice.util.playerutil.PlayerUtil;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PartySplit extends PlayersVsPlayers {

    private final List<Player> partySpectators;

    public PartySplit(Ladder ladder, Arena arena, Party party, int winsNeeded) {
        this(ladder, arena, party, winsNeeded, null, null);
    }

    public PartySplit(Ladder ladder, Arena arena, Party party, int winsNeeded, @Nullable Map<TeamEnum, List<Player>> assignedTeams, @Nullable List<Player> spectators) {
        super(ladder, arena, fighters(party, spectators), winsNeeded);

        this.type = MatchType.PARTY_SPLIT;
        this.partySpectators = spectators == null ? Collections.emptyList() : new ArrayList<>(spectators);

        if (assignedTeams != null && !assignedTeams.isEmpty()) {
            for (TeamEnum team : List.of(TeamEnum.TEAM1, TeamEnum.TEAM2)) {
                for (Player player : assignedTeams.getOrDefault(team, Collections.emptyList())) {
                    addToTeam(player, team);
                }
            }
            return;
        }

        Collections.shuffle(this.players);
        int team1PlayerCount = 0;
        int team2PlayerCount = 0;
        for (Player player : players) {
            if (team2PlayerCount > team1PlayerCount) {
                addToTeam(player, TeamEnum.TEAM1);
                team1PlayerCount++;
            } else {
                addToTeam(player, TeamEnum.TEAM2);
                team2PlayerCount++;
            }
        }
    }

    private static List<Player> fighters(Party party, @Nullable List<Player> spectators) {
        List<Player> fighters = new ArrayList<>(party.getMembers());
        if (spectators != null) {
            fighters.removeAll(spectators);
        }
        return fighters;
    }

    @Override
    public void startMatch() {
        super.startMatch();

        if (this.partySpectators.isEmpty()) return;

        // The leader put these players into the match as spectators, so they must
        // not be rejected by the regular spectate permission check.
        this.setAllowSpectators(true);

        for (Player spectator : this.partySpectators) {
            this.addSpectator(spectator, null, true, false);
        }
    }

    private void addToTeam(Player player, TeamEnum team) {
        this.teams.get(team).add(player);
        this.originalTeams.get(team).add(player); // Track original team members
        NametagManager.getInstance().setNametag(player, team.getPrefix(), team.getNameColor(), team.getSuffix(), team == TeamEnum.TEAM1 ? 20 : 21);
    }

    @Override
    public void startNextRound() {
        PartySplitRound round = new PartySplitRound(this, this.rounds.size() + 1);
        this.rounds.put(round.getRoundNumber(), round);

        if (round.getRoundNumber() == 1) {
            for (String line : LanguageManager.getList("MATCH.PARTY-SPLIT.MATCH-START")) {
                this.sendMessage(line
                        .replace("%matchTypeName%", MatchType.PARTY_SPLIT.getName(false))
                        .replace("%ladder%", ladder.getDisplayName())
                        .replace("%map%", arena.getDisplayName())
                        .replace("%rounds%", String.valueOf(this.winsNeeded))
                        .replace("%team1name%", TeamEnum.TEAM1.getNameMM())
                        .replace("%team2name%", TeamEnum.TEAM2.getNameMM())
                        .replace("%team1players%", PlayerUtil.getPlayerNames(teams.get(TeamEnum.TEAM1)).toString().replace("[", "").replace("]", ""))
                        .replace("%team2players%", PlayerUtil.getPlayerNames(teams.get(TeamEnum.TEAM2)).toString().replace("[", "").replace("]", "")), false);
            }
        }

        round.startRound();
    }
}

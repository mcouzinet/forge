package forge.ai.ability;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.ability.AbilityFactory;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.ai.ComputerUtil;
import forge.ai.PlayerControllerAi;
import forge.game.GameEntity;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.player.DelayedReveal;
import forge.util.collect.FCollectionView;
import java.util.Map;
import forge.game.replacement.ReplacementEffect;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
import forge.game.trigger.Trigger;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Exchanging text boxes (Deadpool, Trading Card) exchanges the text boxes as they currently are,
 * including text a permanent got from an earlier exchange.
 */
public class TextBoxExchangeTest extends AITest {

    private void exchange(Player p, Card deadpool, Card other) {
        SpellAbility sa = AbilityFactory.getAbility("DB$ ExchangeTextBox | Defined$ Self & ChosenCard", deadpool);
        sa.setActivatingPlayer(p);
        deadpool.setChosenCards(List.of(other));
        AbilityUtils.resolve(sa);
    }

    private static boolean hasTrigger(Card c, String descr) {
        for (Trigger t : c.getTriggers()) {
            if (t.getParamOrDefault("TriggerDescription", "").contains(descr)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasReplacement(Card c, String descr) {
        for (ReplacementEffect re : c.getReplacementEffects()) {
            if (re.getParamOrDefault("Description", "").contains(descr)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasStatic(Card c, String descr) {
        for (StaticAbility st : c.getStaticAbilities()) {
            if (st.getParamOrDefault("Description", "").contains(descr)) {
                return true;
            }
        }
        return false;
    }

    private void assertHasValgavothText(Card c) {
        assertTrue(c.hasKeyword(Keyword.FLYING), c + " should have flying");
        assertTrue(c.hasKeyword(Keyword.LIFELINK), c + " should have lifelink");
        assertTrue(c.hasKeyword(Keyword.WARD), c + " should have ward");
        assertTrue(hasReplacement(c, "exile it instead"), c + " should have Valgavoth's replacement effect");
        assertTrue(hasStatic(c, "you may play cards exiled with"), c + " should have Valgavoth's static ability");
        assertFalse(hasTrigger(c, "you lose 3 life"), c + " should not have Deadpool's upkeep trigger");
    }

    private void assertHasDeadpoolText(Card c) {
        assertFalse(c.hasKeyword(Keyword.FLYING), c + " should not have flying");
        assertFalse(c.hasKeyword(Keyword.LIFELINK), c + " should not have lifelink");
        assertFalse(c.hasKeyword(Keyword.WARD), c + " should not have ward");
        assertFalse(hasReplacement(c, "exile it instead"), c + " should not have Valgavoth's replacement effect");
        assertFalse(hasStatic(c, "you may play cards exiled with"), c + " should not have Valgavoth's static ability");
        assertTrue(hasTrigger(c, "you lose 3 life"), c + " should have Deadpool's upkeep trigger");
    }

    @Test
    public void exchangeWithACreatureThatAlreadyExchangedItsTextBox() {
        Game game = initAndCreateGame();
        Player p1 = game.getPlayers().get(0);
        Player p2 = game.getPlayers().get(1);

        Card valgavoth = addCard("Valgavoth, Terror Eater", p1);
        Card deadpool2 = addCard("Deadpool, Trading Card", p2);
        game.getAction().checkStateEffects(true);

        exchange(p2, deadpool2, valgavoth);
        game.getAction().checkStateEffects(true);
        assertHasValgavothText(deadpool2);
        assertHasDeadpoolText(valgavoth);

        Card deadpool1 = addCard("Deadpool, Trading Card", p1);
        game.getAction().checkStateEffects(true);

        exchange(p1, deadpool1, deadpool2);
        game.getAction().checkStateEffects(true);
        assertHasValgavothText(deadpool1);
        assertHasDeadpoolText(deadpool2);
        assertHasDeadpoolText(valgavoth);
        assertEquals(deadpool1.getName(), "Deadpool, Trading Card");
    }

    /** Casts Deadpool for the given player, who takes the exchange with the given creature. */
    private Card castDeadpool(Game game, Player p, Card exchangeWith) {
        Card deadpool = addCardToZone("Deadpool, Trading Card", p, ZoneType.Hand);
        addCards("Swamp", 2, p);
        addCards("Mountain", 2, p);
        game.getAction().checkStateEffects(true);

        p.dangerouslySetController(new PlayerControllerAi(game, p, p.getLobbyPlayer()) {
            @Override
            public boolean confirmReplacementEffect(ReplacementEffect re, SpellAbility effectSA, GameEntity affected, String question) {
                return true;
            }
            @Override
            public <T extends GameEntity> T chooseSingleEntityForEffect(FCollectionView<T> optionList, DelayedReveal delayedReveal, SpellAbility sa, String title, boolean isOptional, Player targetedPlayer, Map<String, Object> params) {
                for (T o : optionList) {
                    if (o.equals(exchangeWith)) {
                        return o;
                    }
                }
                return super.chooseSingleEntityForEffect(optionList, delayedReveal, sa, title, isOptional, targetedPlayer, params);
            }
            @Override
            public CardCollectionView chooseCardsForEffect(CardCollectionView sourceList, SpellAbility sa, String title, int min, int max, boolean isOptional, Map<String, Object> params) {
                if (sourceList.contains(exchangeWith)) {
                    return new CardCollection(exchangeWith);
                }
                return super.chooseCardsForEffect(sourceList, sa, title, min, max, isOptional, params);
            }
        });

        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);
        SpellAbility cast = deadpool.getSpellAbilities().get(0);
        cast.setActivatingPlayer(p);
        assertTrue(ComputerUtil.playStack(cast, p, game));
        playUntilStackClear(game);
        return game.getCardState(deadpool);
    }

    @Test
    public void castDeadpoolOnADeadpoolThatAlreadyExchangedItsTextBox() {
        Game game = initAndCreateGame();
        Player p1 = game.getPlayers().get(0);
        Player p2 = game.getPlayers().get(1);

        Card valgavoth = addCard("Valgavoth, Terror Eater", p1);
        Card deadpool2 = castDeadpool(game, p2, valgavoth);
        assertHasValgavothText(deadpool2);
        assertHasDeadpoolText(valgavoth);

        Card deadpool1 = castDeadpool(game, p1, deadpool2);
        assertHasValgavothText(deadpool1);
        assertHasDeadpoolText(deadpool2);
        assertHasDeadpoolText(valgavoth);

        // the exchanged text boxes stay after the game moves on
        playUntilNextTurn(game);
        playUntilNextTurn(game);
        assertHasValgavothText(game.getCardState(deadpool1));
        assertHasDeadpoolText(game.getCardState(deadpool2));
        assertHasDeadpoolText(game.getCardState(valgavoth));
    }
}

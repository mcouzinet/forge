package forge.ai;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

public class CloudFormalwearTest extends AITest {

    @Test
    public void cloudReturnedByFormalwearSearchesOnlyOnce() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);

        addCards("Plains", 3, ai);
        Card formalwear = addCardToZone("Pre-War Formalwear", ai, ZoneType.Hand);
        addCardToZone("Cloud, Midgar Mercenary", ai, ZoneType.Graveyard);
        for (int i = 0; i < 3; i++) {
            addCardToZone("Bonesplitter", ai, ZoneType.Library);
        }
        for (int i = 0; i < 10; i++) {
            addCardToZone("Plains", ai, ZoneType.Library);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);

        SpellAbility cast = formalwear.getSpellAbilities().get(0);
        cast.setActivatingPlayer(ai);
        AssertJUnit.assertTrue(ComputerUtil.playStack(cast, ai, game));
        playUntilPhase(game, PhaseType.END_OF_TURN);

        Card cloud = findCardWithName(game, "Cloud, Midgar Mercenary");
        AssertJUnit.assertTrue(cloud.isInZone(ZoneType.Battlefield));
        AssertJUnit.assertTrue(cloud.isEquipped());
        int inHand = (int) ai.getCardsIn(ZoneType.Hand).stream().filter(c -> c.getName().equals("Bonesplitter")).count();
        AssertJUnit.assertEquals("Cloud wasn't equipped when it entered, so it should search only once", 1, inHand);
    }
}

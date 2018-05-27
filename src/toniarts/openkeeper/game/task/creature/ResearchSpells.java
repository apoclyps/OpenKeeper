/*
 * Copyright (C) 2014-2016 OpenKeeper
 *
 * OpenKeeper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenKeeper is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenKeeper.  If not, see <http://www.gnu.org/licenses/>.
 */
package toniarts.openkeeper.game.task.creature;

import com.jme3.math.Vector2f;
import toniarts.openkeeper.game.controller.IGameWorldController;
import toniarts.openkeeper.game.controller.IMapController;
import toniarts.openkeeper.game.controller.creature.ICreatureController;
import toniarts.openkeeper.game.controller.player.PlayerSpellControl;
import toniarts.openkeeper.game.controller.room.AbstractRoomController.ObjectType;
import toniarts.openkeeper.game.controller.room.IRoomController;
import toniarts.openkeeper.game.task.AbstractCapacityCriticalRoomTask;
import toniarts.openkeeper.game.task.TaskManager;
import toniarts.openkeeper.tools.convert.map.ArtResource;
import toniarts.openkeeper.utils.WorldUtils;

/**
 * Research spells for the keeper
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public class ResearchSpells extends AbstractCapacityCriticalRoomTask {

    private final PlayerSpellControl spellControl;

    public ResearchSpells(final IGameWorldController gameWorldController, final IMapController mapController, int x, int y, short playerId, IRoomController room, TaskManager taskManager) {
        super(gameWorldController, mapController, x, y, playerId, room, taskManager);
        spellControl = null;
        //worldState.getGameState().getPlayer(playerId).getSpellControl();
    }

    @Override
    public boolean isValid(ICreatureController creature) {
        return (spellControl.isAnythingToReaseach() && !getRoom().isFullCapacity());
    }

    @Override
    public Vector2f getTarget(ICreatureController creature) {
        return WorldUtils.pointToVector2f(getTaskLocation()); // FIXME 0.5f not needed?
    }

    @Override
    protected String getStringId() {
        return "2625";
    }

    @Override
    protected ObjectType getRoomObjectType() {
        return ObjectType.RESEARCHER;
    }

    @Override
    public void executeTask(ICreatureController creature, float executionDuration) {

        // Advance players spell research
        //PlayerSpell playerSpell = spellControl.research(creature.getCreature().getAttributes().getResearchPerSecond());
       // if (playerSpell != null) {

            // Create a spell book
        //    getRoom().getObjectControl(GenericRoom.ObjectType.SPELL_BOOK).addItem(playerSpell, null, worldState.getThingLoader(), creature);
       // }
    }

    @Override
    public ArtResource getTaskAnimation(ICreatureController creature) {
        //return creature.getCreature().getAnimResearchResource();
        return null;
    }

    @Override
    public String getTaskIcon() {
        return "Textures/GUI/moods/SJ-Library.png";
    }
}

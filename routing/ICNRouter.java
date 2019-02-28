/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package routing;

import core.Connection;
import core.Message;
import core.Settings;

/**
 *
 * @author psxvsh
 */
public class ICNRouter extends MessageRouter {

    public ICNRouter(Settings s) {
        super(s);
    }

    /**
     * Copy-constructor.
     *
     * @param r Router to copy the settings from.
     */
    protected ICNRouter(ICNRouter r) {
        super(r);
    }

    @Override
    public void update() {
        super.update();
    }
    public boolean createNewMessage(Message m) {
            System.out.println("WTF123");
		
		return true;
	}
    @Override
    public void changedConnection(Connection con) {
        // -"-
    }

    @Override
    public MessageRouter replicate() {
        return new ICNRouter(this);
    }
}

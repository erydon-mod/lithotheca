package com.oliver.lithotheca;

import com.oliver.lithotheca.rack.RackNetworking;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Lithotheca implements ModInitializer {
    public static final String ID = "lithotheca";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);

    public static Identifier id(String path) {
        return new Identifier(ID, path);
    }

    @Override
    public void onInitialize() {
        """

            #      #####  #####  #   #   ###   #####  #   #  #####   ####   ###
            #        #      #    #   #  #   #    #    #   #  #      #      #   #
            #        #      #    #   #  #   #    #    #   #  #      #      #   #
            #        #      #    #####  #   #    #    #####  ####   #      #####
            #        #      #    #   #  #   #    #    #   #  #      #      #   #
            #        #      #    #   #  #   #    #    #   #  #      #      #   #
            #####  #####    #    #   #   ###     #    #   #  #####   ####  #   #

            """.lines().forEach(LOGGER::info);
        RackNetworking.initialize();
    }
}

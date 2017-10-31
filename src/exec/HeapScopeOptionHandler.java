package exec;

import java.util.Map;

import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.MapOptionHandler;
import org.kohsuke.args4j.spi.Setter;

public class HeapScopeOptionHandler extends MapOptionHandler {

	public HeapScopeOptionHandler(CmdLineParser parser, OptionDef option, Setter<? super Map<?, ?>> setter) {
		super(parser, option, setter);
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void addToMap(@SuppressWarnings("rawtypes") Map m, String key, String value) {
		try {
			Integer valueInteger = Integer.valueOf(value);
			m.put(key, valueInteger);
		} catch (NumberFormatException e) {
			//here I would like to throw a CmdLineException, but I can't
			//because of the signature of addToMap; so i will just give up
		}
	}
}

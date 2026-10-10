package se.metacraft.config.extensions;

public interface ModificationAware<T extends ModificationAware<T>> {

	T onModified(T oldConfig);

}

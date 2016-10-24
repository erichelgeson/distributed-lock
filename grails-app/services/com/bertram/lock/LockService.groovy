package com.bertram.lock

import com.bertram.lock.conf.LockServiceConfigurer
import com.bertram.lock.provider.LockProvider

/**
 * Grails service that wraps a specific locking provider
 */
class LockService implements GroovyInterceptable {

	static transactional = false

	def grailsApplication

	private LockProvider providerDelegate = null

    def withLockByDomain(Object domainInstance, Closure clos) {
        return withLockByDomain(domainInstance, null, clos)
    }

    def withLockByDomain(Object domainInstance, Map params, Closure clos) {
        def acquired
        try {
            acquired = acquireLockByDomain(domainInstance, params)
            if (acquired)
                return clos.call()
            else
                log.warn("Unable to obtain lock for: ${domainInstance} - ${params}")
        }
        finally {
            if (acquired)
                releaseLockByDomain(domainInstance, params)
        }
    }
    def withLock(String name, Closure clos) {
        return withLock(name, null, clos)
    }

    /**
     * Method handles the lock negotiation for its user while executing some runtime implementation provided by a closure
     * @param name
     * @param params
     * @param clos
     * @return
     */
    def withLock(String name, Map params, Closure clos) {
        def acquired
        try {
            acquired = acquireLock(name, params)
            if (acquired) {
                return clos.call()
            }
            else {
                log.warn("Unable to obtain lock for: ${name} - ${params}")
            }
        }
        finally {
            if (acquired) // Only release if we succesfully acquired a lock
                releaseLock(name, params)
        }
    }
	/**
	 * acquire lock with no extra arguments
	 * @param name
	 * @return
	 */
	def acquireLock(String name) {
		return acquireLock(name, null)
	}

	/**
	 * Standard method to acquire a lock by name/key
	 * @param name
	 * @param params
	 * @return
	 */
	def acquireLock(String name, Map params) {
		return providerDelegate.acquireLock(name, params)
	}

	/**
	 * acquire lock by domain with no extra arguments
	 * @param domainInstance
	 * @return
	 */
	def acquireLockByDomain(Object domainInstance) {
		return acquireLockByDomain(domainInstance, null)
	}

	/**
	 * Convenience method to acquire a lock by a grails domain instance
	 * @param domainInstance
	 * @param params
	 * @return
	 */
	def acquireLockByDomain(Object domainInstance, Map params) {
		if (isDomain(domainInstance)) {
			return providerDelegate.acquireLock("${domainInstance.class.name}:${domainInstance.id}", params)
		}
		else {
			log.error("${domainInstance.class.name} is not a valid domain class")
			if ((params?.raiseError != null ? params.raiseError : providerDelegate.raiseError))
				throw new RuntimeException("Unable to acquire lock for instance of ${domainInstance.class.name}: It is not a valid domain class")
			else
				return false
		}
	}

	/**
	 * release a lock with no extra arguments
	 * @param name
	 * @return
	 */
	def releaseLock(String name) {
		return releaseLock(name, null)
	}

	/**
	 * Used to release an active lock
	 * @param name
	 * @param params
	 * @return
	 */
	def releaseLock(String name, Map params) {
		return providerDelegate.releaseLock(name, params)
	}

	/**
	 * release a lock by domain instance with no extra arguments
	 * @param domainInstance
	 * @return
	 */
	def releaseLockByDomain(Object domainInstance) {
		return releaseLockByDomain(domainInstance, null)
	}

	/**
	 * Convenience method for releasing a lock generated by a domain class
	 * @param domainInstance
	 * @param params
	 */
	def releaseLockByDomain(Object domainInstance, Map params) {
		if (isDomain(domainInstance)) {
			return providerDelegate.releaseLock("${domainInstance.class.name}:${domainInstance.id}", params)
		}
		else {
			log.error("${domainInstance.class.name} is not a valid domain class")
			if ((params?.raiseError != null ? params.raiseError : delegate.raiseError))
				throw new RuntimeException("Unable to release lock for instance of ${domainInstance.class.name}: It is not a valid domain class")
			else
				return false
		}
	}

	/**
	 * renew a lock with no extra arguments
	 * @param name
	 * @return
	 */
	def renewLock(String name) {
		return renewLock(name, null)
	}

	/**
	 * Method is used to reset the expiration of an existing active lock
	 * @param name
	 * @param params
	 * @return
	 */
	def renewLock(String name, Map params) {
		return providerDelegate.renewLock(name, params)
	}

	/**
	* Method is used to see if a lock has already been acquired or not
	* @param name
	* @param params
	* @return
	*/
	String checkLock(String name, Map params) {
		return providerDelegate.checkLock(name, params)
	}

	/**
	 * renew a lock by domain class with no extra arguments
	 * @param domainInstance
	 * @return
	 */
	def renewLockByDomain(Object domainInstance) {
		return renewLockByDomain(domainInstance, null)
	}

	/**
	 * renew a lock by a domain class instance
	 * @param domainInstance
	 * @param params
	 * @return
	 */
	def renewLockByDomain(Object domainInstance, Map params) {
		if (isDomain(domainInstance)) {
			return providerDelegate.renewLock("${domainInstance.class.name}:${domainInstance.id}", params)
		}
		else {
			log.error("${domainInstance.class.name} is not a valid domain class")
			if ((params?.raiseError != null ? params.raiseError : providerDelegate.raiseError))
				throw new RuntimeException("Unable to renew lock for instance of ${domainInstance.class.name}: It is not a valid domain class")
			else
				return false
		}
	}

	/**
	 * This method will return a Set of strings identifying the active locks using by this application
	 * @return
	 */
	def getLocks(){
		return providerDelegate.locks
	}

	/**
	 * utility method to determine if instance is domain class
	 * @param instance
	 * @return
	 */
	private Boolean isDomain(instance) {
		def className
		try {
			className = instance.handler.entityName
		}
		catch (Throwable t) {
			className = instance.class.name
		}

		def gc = grailsApplication.getArtefact('Domain', className)
		return (gc ? true : false)
	}

	/**
	 * Intercept method calls and cache lock provider on instance as opposed to fetching from spring context every time
	 * @param name
	 * @param args
	 */
	def invokeMethod(String name, args) {
		if (!providerDelegate)
			providerDelegate = grailsApplication.mainContext.getBean(LockServiceConfigurer.getLockServiceBeanName())

		def metaMethod = this.metaClass.getMetaMethod(name, args)

        if (!metaMethod)
            throw new MissingMethodException(name, LockService.class, args)

		return metaMethod.invoke(this, args)
	}
}

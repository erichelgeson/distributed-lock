package com.bertram.lock



import spock.lang.*

/**
 * Integration tests for LockService
 */
class LockServiceIntegrationSpec extends Specification {
	def lockService

    def setup() {
    }

    def cleanup() {
    }

    void "test acquireLock() with redis provider"() {
	    when:
	    def lock1 = lockService.acquireLock('lock1', [timeout:8000l, raiseError:true])
	    def lock2 = lockService.acquireLock('lock2', [timeout:8000l, raiseError:true])

	    then:
	    lock1
	    lock2
	    lockService.acquireLock('lock1', [timeout:1000l]) == false
	    lockService.acquireLock('lock2', [timeout:1000l]) == false

	    cleanup:
	    lockService.releaseLock('lock1')
	    lockService.releaseLock('lock2')

    }

	void "test getting all active locks"() {
		when:
		def lock1 = lockService.acquireLock('lock101')
		def lock2 = lockService.acquireLock('lock102')

		then:
		lock1
		lock2
		lockService.locks.size() == 2

		cleanup:
		lockService.releaseLock('lock101')
		lockService.releaseLock('lock102')
	}
}
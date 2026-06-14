https://www.youtube.com/watch?v=kuwo9m-K9Bg | 
JPMC: A Payments Modernization Journey using Temporal | Replay 2024This video features a conversation between **Rob** (from *Temporal*) and **Rajesh Iyer** (from *JP Morgan Chase - CCB*) discussing the bank's digital transformation and their adoption of *Temporal* for **payments modernization**.

### **Key Takeaways:**

*   **Modernizing Financial Infrastructure:** *JP Morgan Chase* processes approximately *$10 trillion* daily. To improve reliability and scalability, the team is shifting away from monolithic legacy systems toward **cloud-native microservices** on *AWS* (11:42 - 12:39).
*   **Why Temporal?** The bank evaluated several solutions (*Netflix Conductor, Axon, Spring Support*) and selected *Temporal* to handle **durable execution**. It abstracts complex patterns—such as **retry handling, state management, event sourcing, and compensation controls**—allowing developers to focus solely on **business logic** (3:51 - 4:10, 6:40 - 7:36).
*   **Open Source Strategy:** *JP Morgan Chase* prioritizes open-source technologies because they provide the flexibility to customize components (like security, authentication, and encryption) that off-the-shelf commercial products often restrict (8:13 - 9:02).
*   **Strategic Patience:** Implementing these changes in a massive institution requires a long-term approach. The team spent over a year and a half in the evaluation and approval phase to ensure compliance and architectural alignment (16:03 - 16:58).
*   **Future Innovations:** The bank is actively integrating new capabilities, including **travel, dining, and 'Pay-in-4' lending features**, into their existing consumer mobile apps as part of this modernization journey (12:48 - 14:02).
https://www.youtube.com/watch?v=3ybR_6dHkWMEvaluation and use of Temporal within the JPMC tech stack | Replay 2023
This video features a discussion between *Jim Walker* from *Temporal* and representatives from *JPMorgan Chase*—*Rajesh Hayer* and *Devon Thompson*—regarding the bank's adoption and evaluation of *Temporal* for their payment infrastructure.

### **Key Takeaways:**

*   **Modernization and Tech Identity:** *JPMorgan Chase* is actively transforming its technology stack, moving away from legacy monolithic systems toward microservices on the public cloud (specifically *AWS*) to function more like a technology company (0:26-1:53).
*   **Why Temporal:** After evaluating alternatives like *Spring State Machine*, *Axon Sagas*, and *AWS Step Functions*, the bank selected *Temporal* for its ability to provide **durable execution**, **workflows-as-code**, and superior **testability** (3:55-6:45).
*   **Payment Use Cases:** *Temporal* is being integrated into the core of consumer and small business payments (such as *Zelle*, direct deposits, and bill pay). It is expected to help standardize processes like fraud checks and anti-money laundering validations (8:15-10:51).
*   **Operational Benefits:** The primary value lies in replacing complex, "batchy" manual processes and boilerplates with reliable, observable workflows. This reduces production incidents and allows developers to focus on business logic rather than state management (13:28-14:07, 22:00-23:18).
*   **Implementation Strategy:** The bank is taking an incremental approach, peeling off specific functions from legacy applications to wrap them in *Temporal* workflows, rather than attempting a high-risk "big bang" migration (17:15-18:48).
*   **Future Needs:** The team is particularly interested in features like **workflow updates** to handle real-time customer interactions, **global namespaces** for high availability across regions, and improved **versioning** (23:30-24:35).

Throughout the conversation, the speakers emphasize that *JPMorgan Chase* maintains rigorous security and compliance standards for all open-source integrations, confirming that their move to the cloud and modern tooling is carefully governed (25:05-26:50).
https://www.youtube.com/watch?v=gVucnhnyYTw
From Monolith to State-of-the-Art Banking • Flavio Deroo • GOTO 2022This presentation by **Flavio Deroo**, Staff Engineer at *Solarisbank*, details the challenging journey of migrating their legacy core banking system to a modern, cloud-native architecture. The talk covers the transition from a monolithic structure to a high-scale, distributed system using **event sourcing** and **CQRS**.

### **Key Phases of the Journey**
* **The Initial Monolith (03:11 - 11:38):** Initially, to meet startup speed requirements, *Solarisbank* built a product layer around a purchased core banking system. This created a "monolith of all monoliths" that suffered from poor performance, bottlenecks, and maintenance challenges.
* **The Architectural Shift (11:38 - 25:38):** To scale effectively, they moved to a microservices-based architecture built on **Golang**, **Kubernetes**, and **AWS**. They adopted **Event Sourcing** (capturing every change as an immutable sequence of events) and **CQRS** to ensure transparency, security, and scalability.
* **The Migration Plan (25:38 - 35:06):** Migrating millions of customers without downtime required years of planning. This included:
    *   **Double writing** data to both systems to ensure parity.
    *   **Backfilling 300+ million records** from the legacy SQL database to the new DynamoDB-based event store.
    *   Rigorous **reconciliation** to ensure 0% data inconsistency before fully cutting over to the new platform.

### **Key Takeaways**
* **You cannot optimize a bad design:** Building a new, scalable system from scratch was necessary when the monolith hit its limits.
* **Event Sourcing is powerful for Banking:** While it requires significant upfront investment in code and complexity, it provides an unparalleled audit trail and data reliability.
* **Embrace Eventual Consistency:** To achieve high availability in a distributed banking environment, the team had to move away from strictly synchronous operations for many processes, managing consistency through robust monitoring and asynchronous event processing.
https://www.youtube.com/watch?v=UPXti9PICbw
Crafting the Core - Why and How We Built the Core Banking System • Armin Pasalic • GOTO 2020
This presentation by **Armin Pasalic** at **GOTOpia Europe 2020** explores the journey of *Solarisbank* in building their own **core banking system** from scratch. The talk covers the technical challenges, architectural decisions, and the evolution of their platform.

### **Key Themes & Journey**
* **The Challenge:** Initially, the startup outsourced its core banking infrastructure. As the company grew, they encountered significant **scalability, reliability, and coupling issues** with the third-party provider, prompting the need for an in-house solution (05:18 - 08:40).
* **The Research Phase:** A small research team was formed in 2016 to reduce dependency on the external system. They focused on **Domain-Driven Design (DDD)**, event sourcing, and **CQRS** (Command Query Responsibility Segregation) to create a more flexible architecture (08:40 - 12:15).
* **Architectural Shifts:** The team moved from an illusion of immediate consistency to **eventual consistency**, which better matched the reality of their distributed business processes. They also adopted **asynchronous messaging** to stabilize the system and decouple services (14:40 - 20:44).
* **Scaling and Resilience:** The transition involved learning from failures, such as initial issues with singleton services, and moving to a cloud-native approach on *AWS* using *SNS* and *SQS* for better horizontal scaling (24:16 - 27:00).
* **Go-Live:** By late 2019, the new system went fully live. By mid-2020, it was processing a significant portion of the bank's transactions, demonstrating the effectiveness of the new, resilient architecture (30:12 - 32:23).

### **Final Takeaways**
* **

There is no magic in software engineering, Pasalic emphasizes. Success comes from understanding fundamentals, maintaining clean architecture, and the hard work of iterating on complex systems to ensure they can scale and evolve with business needs (32:23 - 34:01).
https://www.youtube.com/watch?v=cvmW6t5u_1g
Fiservs Path to YugabyteDB Modernizing Mission Critical Financial Systems at ScaleThis video features **Jay Duraisamy**, CTO/CIO for Data Commerce Solutions at *Fiserv*, discussing the company's modernization of its mission-critical financial systems using **YugabyteDB**. Fiserv processes payments for millions of merchants and financial institutions, handling a massive scale of global transactions.

**Key takeaways include:**

* **The Challenge:** Fiserv needed an architecture that is highly scalable, highly available, and cloud-agnostic to process billions of consumer interactions (1:34 - 2:27).
* **The Solution:** By transitioning from legacy SQL and NoSQL databases to **YugabyteDB** (integrated with *Databricks*), Fiserv created an 'entity graph' that connects consumer data across multiple business units (3:19 - 3:34).
* **Performance:** The platform maintains low-latency performance, often achieving query times of less than 20 milliseconds for critical consumer IDs, even while handling massive batch processing volumes (6:50 - 7:01, 9:20 - 9:35).
* **Operational Lessons:** Duraisamy highlights the importance of planning for keys and indexes, being aware of the costs associated with secondary indexes, and managing data compaction (9:58 - 11:42).
* **Future Roadmap:** For 2026, Fiserv plans to leverage **PG Vector** for AI/LLM use cases, expand multi-cloud replication across *AWS*, and continue migrating existing *Postgres* workloads to YugabyteDB (14:08 - 15:16).

During the Q&A session, Duraisamy notes that while they process massive volumes—often ranging from 20 to 30 billion records daily across various sources—they implement strict data governance to ensure compliance with financial regulations regarding data retention (15:26 - 17:55).
https://www.youtube.com/watch?v=SKYbOk2S-xo
Building Better Software: Why Workflows Beat Code Every Time • Ben Smith & James Beswick • GOTO 2025This video features a conversation between *Ben Smith* and *James Beswick* from *Stripe*, exploring modern software architecture, with a specific focus on **workflow services** and their role in distributed systems. 

### **Key Takeaways:**

*   **The Power of Workflows:** The speakers discuss why workflows can be superior to raw code for managing complex processes. They argue that workflows reduce the amount of code to manage, offer built-in observability, and provide visual representations of state machines, which helps with debugging and understanding long-term architectural decisions (0:27 - 6:03).
*   **Handling Complexity:** The discussion covers critical patterns for distributed systems, such as:
    *   **Idempotency:** Ensuring that repeating the same action yields the same result, which is vital for payment-related services (1:47 - 2:00, 7:42 - 9:05).
    *   **Circuit Breaker Pattern:** A method to prevent constant failures by stopping requests to a service known to be broken (3:56 - 5:04).
*   **Microservices vs. Monoliths:** They debate the shift from monoliths to microservices and back, noting that while microservices offer decoupling, they can introduce high cognitive load and fragility. They highlight the importance of designing boundaries that don't over-orchestrate (12:03 - 18:03).
*   **Extensibility & Plugins:** Ben explains how *plugin architectures* (using events as hooks) allow core applications to be extended by third-party developers without risking the stability of the core system (19:22 - 24:26).
*   **Developer Advocacy:** The final segment focuses on the role of a Developer Advocate, emphasizing the need for empathy, clear communication, and the ability to bridge complex technical concepts for diverse audiences (39:57 - 45:21).

Throughout the video, both speakers stress that software development is defined by **trade-offs**. They encourage developers to experiment, build in the open, and accept that getting things wrong is a natural part of the learning process.

Residues: Time, Change & Uncertainty in Software Architecture • Barry O'Reilly • GOTO 2025https://www.youtube.com/watch?v=D8qQUHrksrEIn this presentation from GOTO Copenhagen 2025, *Barry O'Reilly* introduces **Residuality Theory**, a novel approach to software architecture that leverages complexity science to manage uncertainty. O'Reilly argues that traditional architectural methods—focused on logic, structure, or rigid processes—often fail when faced with the inherent unpredictability of complex business environments.

### Key Concepts:

* **Moving beyond reductionism (0:00 - 11:10):** The speaker traces the history of software architecture from the logical focus of the 1930s and the structural focus of the 1960s to modern adaptive/agile processes. He suggests that current tools are often "architectural hindrances" because they attempt to reduce or ignore complexity rather than embrace it.
* **Complexity Science & Attractors (11:10 - 21:15):** Drawing on *Stuart Kauffman’s* work with **Random Boolean Networks**, O'Reilly explains that complex systems naturally settle into a limited number of stable states known as **attractors**. He posits that architecture should focus on these attractors rather than specific requirements.
* **Residuality Theory (21:15 - 38:39):** The core of the theory involves **random stress testing** (or "dart-chucking") of an architecture to identify points of failure. The "residues" are the architectural components designed specifically to ensure survival when the system is pushed into these stress-induced attractor states. This process transforms architecture from a design task into a **training task**.
* **Criticality vs. Correctness (38:39 - 46:08):** O'Reilly shifts the goal of architecture from functional "correctness" to **criticality**—the "edge of chaos" where a system is flexible enough to respond to unexpected stressors (such as real-world edge cases like "icing" or equipment sabotage) without collapsing.

### Practical Application:
* **Stressor Analysis:** Architects should perform random simulations of events (e.g., "What if X happens?") and build residues to survive them, rather than relying on predefined scopes.
* **Contagion Analysis:** Using tools like an **incidence matrix**, architects can reveal hidden, non-functional coupling between components to prevent cascading failures.

The talk concludes by advocating for a more scientific, empirical foundation for architecture, supported by a research-backed methodology that allows systems to survive unknown events through deliberate, stress-based design.
In this presentation from *GOTO Amsterdam 2023*, *Stefan Tilkov* shares **10 practical recommendations** for effective software architecture. He emphasizes that architecture is not just about documentation, but about making active, informed decisions that enable teams to deliver value.
https://www.youtube.com/watch?v=BNTt2aLB1tg
Practical (a.k.a. Actually Useful) Architecture • Stefan Tilkov • GOTO 2023

### **Key Takeaways from the Presentation:**

* **Conscious Perspectives (01:51):** Differentiate between **domain architecture** (logical units), **macro architecture** (how systems collaborate), and **micro architecture** (internal implementation). Mixing these causes problems.
* **Team Setup (07:45):** Architects should influence team organization. Aligning team boundaries with system boundaries (Conway’s Law) allows for greater autonomy and independent work.
* **Organizational Context (13:22):** Your architectural approach must match your project's size and complexity. Avoid copying massive systems (like *Netflix*) if you are a smaller organization.
* **Centralized vs. Autonomous Decisions (17:39):** While autonomy is vital, some foundational decisions (e.g., interfaces, team responsibilities) must be made centrally to avoid chaos.
* **Pick Your Battles (21:05):** Focus on the most critical architectural issues first. Don't try to change every technology or standard simultaneously.
* **Rules and Enforcement (23:37):** Define the **least viable amount of rules** and enforce them rigidly. Provide a 'best practice' path that teams can follow but allow for flexibility where it doesn't break the system.
* **Beyond Documentation (29:52):** Architecture is about **active decision-making**, not just creating diagrams. If you only document, you are likely failing to improve the system.
* **Iterative Process (35:15):** Architecture should not aim for a static, perfect end-state. Instead, build for **evolvability** and handle change gracefully over time.
* **Development Pipeline (36:51):** An architect’s job includes optimizing for **delivery flow**. If an architecture creates bottlenecks, it hinders the teams' ability to provide value.
* **Be Boring (39:20):** Avoid the trap of adopting overly complex or 'cool' technologies just for the sake of it. Prefer simple, proven solutions to keep systems maintainable and cost-effective.

**Conclusion:** 
*Stefan* argues that good architecture is the 'important stuff' that is hard to change later. By focusing on context, team dynamics, and long-term evolvability, architects can provide actual value rather than just overhead.